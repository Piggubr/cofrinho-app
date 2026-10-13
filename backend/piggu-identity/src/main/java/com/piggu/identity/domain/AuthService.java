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
import java.time.Instant;
import java.util.List;
import java.util.Set;
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

    /** Senhas erradas seguidas antes de travar a entrada por senha da conta. */
    static final int TENTATIVAS_DE_SENHA = 5;
    static final Duration TRAVA_DA_SENHA = Duration.ofMinutes(15);

    private static final String SENHA_ERRADA = "E-mail ou senha incorretos.";

    private final GoogleIdTokenVerifier verificador;
    private final UserAccountRepository usuarios;
    private final FamiliaService familias;
    private final RefreshSessionRepository sessoes;
    private final SessionRevoker revogador;
    private final TokenService tokens;
    private final Senhas senhas;
    private final MinhaContaService minhaConta;
    private final Set<String> emailsDeAdmin;

    public AuthService(GoogleIdTokenVerifier verificador,
                       UserAccountRepository usuarios,
                       FamiliaService familias,
                       RefreshSessionRepository sessoes,
                       SessionRevoker revogador,
                       TokenService tokens,
                       Senhas senhas,
                       MinhaContaService minhaConta,
                       @Value("${piggu.admin-emails:}") List<String> emailsDeAdmin) {
        this.verificador = verificador;
        this.usuarios = usuarios;
        this.familias = familias;
        this.sessoes = sessoes;
        this.revogador = revogador;
        this.tokens = tokens;
        this.senhas = senhas;
        this.minhaConta = minhaConta;
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
     * Cadastro por e-mail e senha. O e-mail fica sem prova ate o primeiro login Google
     * nele: por isso nao usa convite de familia nem vira ADMIN pela lista.
     *
     * <p>Quem mora no Brasil informa o CPF aqui mesmo, e ele e conferido antes de a conta
     * nascer: CPF ja usado em outra conta barra o cadastro. Quem mora fora segue sem CPF
     * (se informar um, ele vale igual).</p>
     */
    @Transactional
    public TokenResponse cadastrar(String nome, String emailBruto, String senha, String cpfDigitado,
                                   boolean moraNoBrasil, String versaoDosTermos, boolean lembrar, String userAgent) {
        String email = Texto.email(emailBruto);
        String nomeLimpo = Texto.limitar(Texto.espacoUnico(nome), 120);
        if (nomeLimpo.isEmpty()) {
            throw new BusinessException("Conte como podemos te chamar.");
        }
        if (usuarios.findByEmail(email).isPresent()) {
            throw new BusinessException("Este e-mail ja tem conta no Piggu. Entre com ele; se a conta foi criada"
                    + " pelo Google, entre com o Google e crie uma senha no Perfil.",
                    HttpStatus.CONFLICT, "EMAIL_JA_CADASTRADO");
        }
        senhas.validar(senha, email);
        boolean semCpf = Texto.vazio(cpfDigitado);
        if (moraNoBrasil && semCpf) {
            throw new BusinessException("Informe seu CPF. Ele garante uma conta por pessoa.",
                    HttpStatus.UNPROCESSABLE_CONTENT, "CPF_NECESSARIO");
        }
        Cpf cpf = semCpf ? null : minhaConta.conferirCpfLivre(cpfDigitado);

        UserAccount conta = familias.criarConta(email, nomeLimpo.split(" ")[0], versaoDosTermos, false);
        conta.setNickname(nomeLimpo);
        conta.declararResidencia(moraNoBrasil);
        conta.definirSenha(senhas.hash(senha));
        conta.registrarAcesso();
        usuarios.save(conta);
        if (cpf != null) {
            minhaConta.prenderCpf(conta, cpf);
        }
        log.info("Cadastro por senha: conta={}", conta.getId());

        return emitirPar(conta, userAgent, lembrar);
    }

    /**
     * Entrada por e-mail e senha. A mesma mensagem serve para e-mail sem conta e senha
     * errada; cinco erros seguidos travam a entrada por senha da conta por 15 minutos
     * (o Google continua entrando).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse entrarComSenha(String emailBruto, String senha, boolean lembrar, String userAgent) {
        Instant agora = Instant.now();
        UserAccount conta = usuarios.findByEmail(Texto.email(emailBruto)).orElse(null);
        if (conta == null) {
            senhas.confere(senha, null);
            throw new UnauthorizedException(SENHA_ERRADA);
        }
        if (conta.senhaTravada(agora)) {
            throw new BusinessException("Muitas tentativas erradas. Espere alguns minutos ou entre com o Google.",
                    HttpStatus.TOO_MANY_REQUESTS, "SENHA_TRAVADA");
        }
        if (!conta.temSenha()) {
            senhas.confere(senha, null);
            throw new BusinessException("Esta conta ainda nao tem senha. Entre com o Google e crie uma senha no Perfil.",
                    HttpStatus.UNAUTHORIZED, "CONTA_SEM_SENHA");
        }
        if (!senhas.confere(senha, conta.getPasswordHash())) {
            conta.registrarSenhaErrada(TENTATIVAS_DE_SENHA, TRAVA_DA_SENHA, agora);
            usuarios.save(conta);
            throw new UnauthorizedException(SENHA_ERRADA);
        }
        if (!conta.isActive()) {
            log.warn("Login por senha recusado: conta desativada id={}", conta.getId());
            throw new ForbiddenException("Esta conta foi desativada.");
        }

        conta.registrarSenhaCerta();
        conta.registrarAcesso();
        usuarios.save(conta);
        log.info("Login por senha: conta={}", conta.getId());
        return emitirPar(conta, userAgent, lembrar);
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
        UserAccount conta = usuarios.findById(usuarioId)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
        conta.alterarPreferencias(moeda, conversao, pedido.mostrarCotacao(), fusoValido(pedido.fuso()));
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
