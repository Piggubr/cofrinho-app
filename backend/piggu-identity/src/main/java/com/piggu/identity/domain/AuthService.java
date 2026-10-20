package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.PigguRole;
import com.piggu.common.web.Moedas;
import com.piggu.common.web.Texto;
import com.piggu.identity.api.dto.PreferencesRequest;
import com.piggu.identity.api.dto.TokenResponse;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.google.GoogleIdTokenVerifier;
import com.piggu.identity.google.GoogleProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Entrada, renovacao e saida da conta.
 *
 * <p>Reune o que no Code.gs estava espalhado entre {@code validarUsuario_},
 * {@code criarSessao_}, {@code validarSessao_}, {@code obterOuCriarUsuario_} e
 * {@code limparSessoesExpiradas_}.</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * Senhas erradas seguidas, de um mesmo IP, antes de travar a entrada por senha da conta
     * para esse IP. Quem erra de um lugar nao tranca a dona, que entra de outro.
     */
    static final int TENTATIVAS_DE_SENHA = 5;
    /**
     * Senhas erradas seguidas, de qualquer lugar, antes de travar a conta para todos: segura
     * quem tenta adivinhar de muitos IPs. Alto o bastante para nao virar jeito de trancar alguem.
     */
    static final int TENTATIVAS_DA_CONTA = 50;
    static final Duration TRAVA_DA_SENHA = Duration.ofMinutes(15);
    private static final int ORIGENS_ANTES_DE_LIMPAR = 50_000;

    private static final String SENHA_ERRADA = "E-mail ou senha incorretos. Se voce criou a conta pelo Google, "
            + "entre pelo Google. Muitas tentativas seguidas bloqueiam a senha por 15 minutos.";

    /**
     * Erros por conta e IP. ponytail: em memoria, por instancia; some ao reiniciar (a trava
     * da conta, gravada no banco, continua) e com mais de uma replica vira Redis.
     */
    private final Map<String, ErrosDaOrigem> errosPorOrigem = new ConcurrentHashMap<>();

    private record ErrosDaOrigem(int erros, Instant travadaAte) {
    }

    private final GoogleIdTokenVerifier verificador;
    private final UserAccountRepository usuarios;
    private final FamiliaService familias;
    private final RefreshSessionRepository sessoes;
    private final SessionRevoker revogador;
    private final TokenService tokens;
    private final Senhas senhas;
    private final MinhaContaService minhaConta;
    private final ConfirmacaoDeEmail confirmacao;
    private final Set<String> emailsDeAdmin;

    public AuthService(GoogleIdTokenVerifier verificador,
                       UserAccountRepository usuarios,
                       FamiliaService familias,
                       RefreshSessionRepository sessoes,
                       SessionRevoker revogador,
                       TokenService tokens,
                       Senhas senhas,
                       MinhaContaService minhaConta,
                       ConfirmacaoDeEmail confirmacao,
                       @Value("${piggu.admin-emails:}") List<String> emailsDeAdmin) {
        this.verificador = verificador;
        this.usuarios = usuarios;
        this.familias = familias;
        this.sessoes = sessoes;
        this.revogador = revogador;
        this.tokens = tokens;
        this.senhas = senhas;
        this.minhaConta = minhaConta;
        this.confirmacao = confirmacao;
        this.emailsDeAdmin = emailsDeAdmin.stream().map(Texto::email).filter(email -> !email.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Troca o ID token do Google por um par de tokens do Piggu.
     * Cria a conta (e a familia, ou entra na que convidou) no primeiro acesso.
     */
    @Transactional
    public TokenResponse entrarComGoogle(String idToken, String versaoDosTermos, String userAgent) {
        return entrarComGoogle(idToken, versaoDosTermos, true, userAgent);
    }

    /** @param lembrar continuar conectado: o cookie do refresh sobrevive a fechar o navegador */
    @Transactional
    public TokenResponse entrarComGoogle(String idToken, String versaoDosTermos, boolean lembrar, String userAgent) {
        GoogleProfile perfil = verificador.verificar(idToken);
        UserAccount conta = usuarios.findByEmail(perfil.email())
                .orElseGet(() -> familias.criarConta(perfil.email(), perfil.givenName(), versaoDosTermos));

        if (!conta.isActive()) {
            log.warn("Login recusado: conta desativada id={}", conta.getId());
            throw new ForbiddenException("Esta conta foi desativada.");
        }

        if (!conta.isEmailVerified()) {
            // Alguem cadastrou este e-mail por senha antes, sem provar que era dele. O Google
            // acabou de provar quem e o dono: a senha de quem cadastrou deixa de valer e as
            // sessoes abertas com ela caem. Se foi o proprio dono, ele cria outra no Perfil.
            if (conta.temSenha()) {
                conta.apagarSenha();
                sessoes.apagarPorUsuario(conta.getId());
                log.warn("Senha apagada no primeiro login Google de conta sem e-mail confirmado: conta={}",
                        conta.getId());
            }
            conta.confirmarEmail();
        }
        conta.atualizarPerfilGoogle(perfil.name(), perfil.givenName(), perfil.picture());
        // Promove, nunca rebaixa: tirar alguem da lista nao derruba um ADMIN por engano.
        if (emailsDeAdmin.contains(conta.getEmail()) && conta.getRole() != PigguRole.ADMIN) {
            conta.setRole(PigguRole.ADMIN);
            log.info("Conta promovida a ADMIN pela PIGGU_ADMIN_EMAILS: conta={}", conta.getId());
        }
        usuarios.save(conta);
        log.info("Login: conta={}", conta.getId());

        return emitirPar(conta, userAgent, lembrar);
    }

    /**
     * Cadastro por e-mail e senha. O e-mail fica sem prova ate a pessoa abrir o link de
     * {@link ConfirmacaoDeEmail} (ou entrar pelo Google com ele): por isso nao usa convite de
     * familia nem vira ADMIN pela lista.
     *
     * <p>No Brasil o CPF e obrigatorio e conferido (digitos), mas CPF repetido nao barra.
     * Fora, o documento de {@link DocumentosDeIdentidade} e opcional; se vier, e conferido
     * e tambem pode repetir. O celular e obrigatorio e fica em E.164.</p>
     */
    @Transactional
    public void cadastrar(String nome, String emailBruto, String senha, String paisDigitado,
                          String documento, String telefone, String versaoDosTermos, LocalDate nascimento) {
        String email = Texto.email(emailBruto);
        String nomeLimpo = Texto.limitar(Texto.espacoUnico(nome), 120);
        if (nomeLimpo.isEmpty()) {
            throw new BusinessException("Conte como podemos te chamar.");
        }
        LocalDate dataDeNascimento = MinhaContaService.conferirNascimento(nascimento);
        senhas.validar(senha, email);
        String pais = DocumentosDeIdentidade.pais(paisDigitado);
        String celular = e164(telefone, pais);
        Cpf cpf = null;
        DocumentosDeIdentidade.Tipo tipo = DocumentosDeIdentidade.pedidoEm(pais).orElse(null);
        String numero = null;
        if (DocumentosDeIdentidade.BRASIL.equals(pais)) {
            if (Texto.vazio(documento)) {
                throw new BusinessException("Informe seu CPF.", HttpStatus.UNPROCESSABLE_CONTENT, "CPF_NECESSARIO");
            }
            cpf = minhaConta.conferirCpf(documento);
        } else if (Texto.vazio(documento)) {
            // Fora do Brasil o documento e opcional; o Perfil oferece informar depois.
        } else if (tipo != null) {
            numero = minhaConta.conferirDocumento(tipo, documento);
        }

        // E-mail que ja tem conta responde igual a um cadastro novo, para o cadastro nao
        // dizer a ninguem quais e-mails usam o Piggu: o dono recebe um aviso no e-mail.
        String hash = senhas.hash(senha);
        Optional<UserAccount> existente = usuarios.findByEmail(email);
        if (existente.isPresent()) {
            confirmacao.avisarCadastroRepetido(existente.get());
            return;
        }
        UserAccount conta = familias.criarConta(email, nomeLimpo.split(" ")[0], versaoDosTermos, false);
        conta.setNickname(nomeLimpo);
        conta.informarNascimento(dataDeNascimento);

        conta.declararPais(pais);
        conta.setPhone(celular);
        conta.definirSenha(hash);
        conta.registrarAcesso();
        usuarios.save(conta);
        if (cpf != null) {
            minhaConta.prenderCpf(conta, cpf);
        }
        if (numero != null) {
            minhaConta.prenderDocumento(conta, tipo, numero);
        }
        confirmacao.enviar(conta);
        // TODO: confirmar o celular por SMS. Precisa de um provedor externo (Twilio, Zenvia,
        // AWS SNS); por ora o numero so fica guardado.
        log.info("Cadastro por senha: conta={}", conta.getId());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse entrarComSenha(String emailBruto, String senha, boolean lembrar, String userAgent) {
        return entrarComSenha(emailBruto, senha, lembrar, userAgent, null);
    }

    /**
     * Entrada por e-mail e senha. A mesma mensagem serve para e-mail sem conta e senha
     * errada. Cinco erros seguidos de um IP travam a entrada por senha da conta para esse
     * IP por 15 minutos; cinquenta, de qualquer lugar, travam para todos. O Google continua
     * entrando.
     *
     * @param origem IP de quem tenta (o mesmo do limite por IP); nulo conta como um lugar so
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse entrarComSenha(String emailBruto, String senha, boolean lembrar, String userAgent,
                                        String origem) {
        Instant agora = Instant.now();
        UserAccount conta = usuarios.findByEmail(Texto.email(emailBruto)).orElse(null);
        if (conta == null) {
            senhas.confere(senha, null);
            throw new UnauthorizedException(SENHA_ERRADA);
        }
        String chave = conta.getId() + "|" + (origem == null ? "" : origem);
        // Travada ou so com Google: a mesma resposta da senha errada, para o login nao
        // revelar quais e-mails tem conta nem como cada uma entra.
        if (conta.senhaTravada(agora) || origemTravada(chave, agora) || !conta.temSenha()) {
            senhas.confere(senha, null);
            throw new UnauthorizedException(SENHA_ERRADA);
        }
        if (!senhas.confere(senha, conta.getPasswordHash())) {
            conta.registrarSenhaErrada(TENTATIVAS_DA_CONTA, TRAVA_DA_SENHA, agora);
            usuarios.save(conta);
            errouDe(chave, agora);
            throw new UnauthorizedException(SENHA_ERRADA);
        }
        if (!conta.isActive()) {
            log.warn("Login por senha recusado: conta desativada id={}", conta.getId());
            throw new ForbiddenException("Esta conta foi desativada.");
        }

        conta.registrarSenhaCerta();
        errosPorOrigem.remove(chave);
        conta.registrarAcesso();
        usuarios.save(conta);
        log.info("Login por senha: conta={}", conta.getId());
        return emitirPar(conta, userAgent, lembrar);
    }

    private boolean origemTravada(String chave, Instant agora) {
        ErrosDaOrigem erros = errosPorOrigem.get(chave);
        return erros != null && erros.travadaAte() != null && erros.travadaAte().isAfter(agora);
    }

    /** Conta um erro deste IP na conta; no limite trava este IP e recomeca a contagem. */
    private void errouDe(String chave, Instant agora) {
        errosPorOrigem.compute(chave, (k, atual) -> {
            int erros = (atual == null || atual.travadaAte() != null ? 0 : atual.erros()) + 1;
            return erros >= TENTATIVAS_DE_SENHA ? new ErrosDaOrigem(0, agora.plus(TRAVA_DA_SENHA))
                    : new ErrosDaOrigem(erros, null);
        });
        if (errosPorOrigem.size() > ORIGENS_ANTES_DE_LIMPAR) {
            errosPorOrigem.values().removeIf(erros -> erros.travadaAte() == null || !erros.travadaAte().isAfter(agora));
        }
    }

    /**
     * Cria a senha (conta que so entrava pelo Google) ou troca a atual, que entao precisa
     * ser informada. Trocar derruba as outras sessoes; a deste aparelho continua.
     *
     * @param refreshAtual cookie da sessao deste aparelho, que nao cai; pode ser nulo
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public UserResponse definirSenha(java.util.UUID usuarioId, String senhaAtual, String novaSenha, String refreshAtual) {
        UserAccount conta = usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
        Instant agora = Instant.now();
        if (conta.temSenha()) {
            if (conta.senhaTravada(agora)) {
                throw new BusinessException("Muitas tentativas erradas. Espere alguns minutos.",
                        HttpStatus.TOO_MANY_REQUESTS, "SENHA_TRAVADA");
            }
            if (!senhas.confere(senhaAtual, conta.getPasswordHash())) {
                conta.registrarSenhaErrada(TENTATIVAS_DE_SENHA, TRAVA_DA_SENHA, agora);
                usuarios.save(conta);
                throw new BusinessException("A senha atual nao confere.", HttpStatus.UNPROCESSABLE_CONTENT,
                        "SENHA_ATUAL_ERRADA");
            }
        }
        senhas.validar(novaSenha, conta.getEmail());

        boolean trocou = conta.temSenha();
        conta.definirSenha(senhas.hash(novaSenha));
        usuarios.save(conta);
        if (trocou) {
            String manter = refreshAtual == null || refreshAtual.isBlank() ? null : tokens.hash(refreshAtual);
            sessoes.findByUserId(conta.getId()).stream()
                    .filter(sessao -> !sessao.getTokenHash().equals(manter))
                    .forEach(sessoes::delete);
        }
        log.info("Senha {}: conta={}", trocou ? "trocada" : "criada", conta.getId());
        return UserResponse.de(conta, familias.daConta(conta));
    }

    /** Renova o acesso e rotaciona a sessao longa: o refresh usado e' descartado. */
    @Transactional
    public TokenResponse renovar(String refreshToken, String userAgent) {
        RefreshSession sessao = sessoes.findByTokenHash(tokens.hash(refreshToken))
                .orElseThrow(() -> new UnauthorizedException("Sua sessao venceu. Entre novamente."));

        if (!sessao.estaValida()) {
            // Revogacao em transacao propria: a excecao abaixo desfaz esta transacao,
            // e um DELETE feito aqui dentro seria desfeito junto.
            revogador.revogar(sessao.getId());
            throw new UnauthorizedException("Sua sessao venceu. Entre novamente.");
        }

        UserAccount conta = usuarios.findById(sessao.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));

        if (!conta.isActive()) {
            log.warn("Renovacao recusada: conta desativada id={}", conta.getId());
            revogador.revogarTodasDe(conta.getId());
            throw new ForbiddenException("Esta conta foi desativada.");
        }

        sessoes.delete(sessao);
        // Quem so renova tambem esta usando: sem isto a retencao apagaria conta ativa.
        conta.registrarAcesso();
        usuarios.save(conta);
        return emitirPar(conta, userAgent, sessao.isRemember());
    }

    @Transactional
    public void sair(String refreshToken) {
        sessoes.findByTokenHash(tokens.hash(refreshToken)).ifPresent(sessoes::delete);
    }

    @Transactional(readOnly = true)
    public UserResponse perfil(java.util.UUID usuarioId) {
        return usuarios.findById(usuarioId)
                .map(conta -> UserResponse.de(conta, familias.daConta(conta)))
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
    }

    @Transactional
    public UserResponse salvarPreferencias(java.util.UUID usuarioId, PreferencesRequest pedido) {
        String moeda = Moedas.validar(pedido.moeda());
        String conversao = Moedas.validar(pedido.moedaConversao());
        String cotacao = pedido.moedaCotacao() == null ? null : Moedas.validar(pedido.moedaCotacao());
        UserAccount conta = usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
        conta.alterarPreferencias(moeda, cotacao, conversao, pedido.mostrarCotacao(), fusoValido(pedido.fuso()));
        return UserResponse.de(conta, familias.daConta(conta));
    }

    @Transactional
    public UserResponse escolherModulos(java.util.UUID usuarioId, List<String> modulos) {
        UserAccount conta = usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
        conta.escolherModulos(modulos);
        return UserResponse.de(conta, familias.daConta(conta));
    }

    @Transactional
    public UserResponse escolherAvisos(java.util.UUID usuarioId, boolean porEmail, boolean porPush) {
        UserAccount conta = usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
        conta.escolherAvisos(porEmail, porPush);
        return UserResponse.de(conta, familias.daConta(conta));
    }

    /**
     * Celular em E.164. Com "+" vale o codigo do pais digitado; sem ele, no Brasil
     * entende DDD + numero e poe o +55.
     */
    static String e164(String digitado, String pais) {
        String bruto = digitado == null ? "" : digitado.trim();
        boolean comCodigo = bruto.startsWith("+");
        String digitos = bruto.replaceAll("\\D", "");
        if (!comCodigo && DocumentosDeIdentidade.BRASIL.equals(pais) && (digitos.length() == 10 || digitos.length() == 11)) {
            digitos = "55" + digitos;
        }
        String numero = "+" + digitos;
        if ((!comCodigo && !DocumentosDeIdentidade.BRASIL.equals(pais)) || !numero.matches("\\+[1-9]\\d{7,14}")) {
            throw new BusinessException("Confira o celular. Fora do Brasil, comece com + e o codigo do pais.",
                    HttpStatus.UNPROCESSABLE_CONTENT, "TELEFONE_INVALIDO");
        }
        return numero;
    }

    private static String fusoValido(String fuso) {
        if (fuso == null || fuso.isBlank()) {
            return null;
        }
        try {
            return java.time.ZoneId.of(fuso.trim()).getId();
        } catch (java.time.DateTimeException invalido) {
            throw new com.piggu.common.error.BusinessException("Fuso horario desconhecido: " + Texto.limitar(fuso, 50));
        }
    }

    private TokenResponse emitirPar(UserAccount conta, String userAgent, boolean lembrar) {
        limparSessoesVencidas();

        String refresh = tokens.gerarRefreshToken();
        sessoes.save(new RefreshSession(
                conta.getId(),
                tokens.hash(refresh),
                Texto.limitar(userAgent, 300),
                tokens.expiracaoDaSessao(),
                lembrar
        ));

        Household familia = familias.daConta(conta);
        return new TokenResponse(
                tokens.gerarAccessToken(conta, familia),
                refresh,
                tokens.segundosDeAcesso(),
                UserResponse.de(conta, familia),
                lembrar
        );
    }

    private void limparSessoesVencidas() {
        try {
            int removidas = sessoes.apagarExpiradas(Instant.now());
            if (removidas > 0) {
                log.debug("{} sessoes vencidas removidas", removidas);
            }
        } catch (RuntimeException erro) {
            log.warn("Nao foi possivel limpar sessoes vencidas agora", erro);
        }
    }
}
