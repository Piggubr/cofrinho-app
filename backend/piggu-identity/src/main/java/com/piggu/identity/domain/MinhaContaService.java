package com.piggu.identity.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Texto;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.avisos.Aparelhos;
import com.piggu.identity.avisos.AvisosEnviados;
import com.piggu.identity.billing.StripePagamentos;
import com.piggu.identity.billing.TestesGratis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Direitos do titular sobre a propria conta (LGPD art. 18): copia de tudo e exclusao.
 *
 * <p>Exportar e apagar seguem gratis em qualquer plano: Premium vencido nunca prende
 * dado da pessoa.</p>
 */
@Service
public class MinhaContaService {

    private static final Logger log = LoggerFactory.getLogger(MinhaContaService.class);

    private final UserAccountRepository usuarios;
    private final RefreshSessionRepository sessoes;
    private final HouseholdRepository familias;
    private final FamiliaService familia;
    private final CascataDeDados cascata;
    private final StripePagamentos pagamentos;
    private final TrilhaDeAuditoria trilha;
    private final CofreDeCpf cofre;
    private final Aparelhos aparelhos;
    private final AvisosEnviados avisosEnviados;
    private final TestesGratis testes;


    public MinhaContaService(UserAccountRepository usuarios,
                             RefreshSessionRepository sessoes,
                             HouseholdRepository familias,
                             FamiliaService familia,
                             CascataDeDados cascata,
                             StripePagamentos pagamentos,
                             TrilhaDeAuditoria trilha,
                             CofreDeCpf cofre,
                             Aparelhos aparelhos,
                             AvisosEnviados avisosEnviados,
                             TestesGratis testes) {
        this.testes = testes;
        this.cofre = cofre;
        this.aparelhos = aparelhos;
        this.avisosEnviados = avisosEnviados;
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.familias = familias;
        this.familia = familia;
        this.cascata = cascata;
        this.pagamentos = pagamentos;
        this.trilha = trilha;
    }

    /**
     * Um arquivo so com o que o Piggu guarda: conta, familia, sessoes abertas e o que
     * cada servico tem. O titular leva a familia inteira; o membro, o que ele lancou.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportar(CurrentUser usuario, String token) {
        UserAccount conta = conta(usuario.id());
        Map<String, Object> arquivo = new LinkedHashMap<>();
        arquivo.put("geradoEm", Instant.now());
        arquivo.put("conta", UserResponse.de(conta, familia.daConta(conta)));
        if (conta.getPhone() != null) {
            arquivo.put("celular", conta.getPhone());
        }
        if (conta.temCpf()) {
            arquivo.put("cpf", Map.of("numero", cofre.decifrar(conta.getCpfCifrado()).formatado(),
                    "informadoEm", conta.getCpfInformadoEm()));
        }
        if (conta.temDocumento()) {
            // Documento de outro pais, de quando o cadastro pedia um (PT_NIF, ES_DNI...).
            arquivo.put("documento", Map.of("tipo", conta.getTipoDoDocumento(),
                    "numero", cofre.decifrarTexto(conta.getDocumentoCifrado()),
                    "informadoEm", conta.getDocumentoInformadoEm()));
        }
        if (conta.getPais() != null) {
            arquivo.put("pais", conta.getPais());
        }
        arquivo.put("familia", familia.ver(usuario));
        if (conta.getTermsVersion() != null) {
            arquivo.put("aceiteDosTermos", Map.of("versao", conta.getTermsVersion(), "em", conta.getTermsAcceptedAt()));
        }
        arquivo.put("sessoes", sessoes.findByUserId(conta.getId()).stream()
                .map(sessao -> Map.of("criadaEm", sessao.getCreatedAt(), "venceEm", sessao.getExpiresAt(),
                        "navegador", Optional.ofNullable(sessao.getUserAgent()).orElse("")))
                .toList());
        arquivo.put("aparelhosQueRecebemAvisos", aparelhos.paraExportar(conta.getId()));
        arquivo.put("avisosEnviados", avisosEnviados.paraExportar(conta.getId()));
        // O titular leva o historico da familia; os demais, o que eles mesmos fizeram.
        arquivo.put("historicoDaFamilia", trilha.recentes(usuario.familia(), null, Integer.MAX_VALUE).stream()
                .filter(evento -> usuario.isTitular() || usuario.id().equals(evento.autor()))
                .toList());
        Map<String, JsonNode> servicos = cascata.exportar(token);
        arquivo.put("dados", servicos);
        log.info("Dados exportados: conta={} servicos={}", conta.getId(), servicos.keySet());
        return arquivo;
    }

    /**
     * Prende o CPF a conta. Pode repetir entre contas; a repeticao vai para o log com as
     * contas envolvidas. Depois de informado nao muda pelo app (correcao pelo canal de
     * privacidade).
     */
    @Transactional
    public UserResponse informarCpf(UUID contaId, String digitado) {
        UserAccount conta = conta(contaId);
        if (conta.temCpf()) {
            throw new BusinessException("Sua conta já tem um CPF. Para corrigir, escreva para o canal de privacidade.",
                    HttpStatus.CONFLICT);
        }
        prenderCpf(conta, conferirCpf(digitado));
        return UserResponse.de(conta, familia.daConta(conta));
    }

    /** Confere o CPF (digitos e formato). Se ja esta em outra conta, quem barra e {@link #prenderCpf}. */
    public Cpf conferirCpf(String digitado) {
        return Cpf.de(digitado);
    }

    /**
     * Grava o CPF ja conferido. Um CPF por conta (Eduardo, 2026-10-22, desfaz a V25): CPF
     * de outra conta e recusado, e o indice unico da V35 garante mesmo com dois cadastros
     * ao mesmo tempo.
     */
    @Transactional
    public void prenderCpf(UserAccount conta, Cpf cpf) {
        String indice = cofre.indice(cpf);
        boolean deOutraConta = usuarios.findByCpfIndice(indice).stream()
                .anyMatch(outra -> !outra.getId().equals(conta.getId()));
        if (deOutraConta) {
            log.warn("CPF de outra conta recusado: conta={}", conta.getId());
            throw new BusinessException(
                    "Esse CPF já está em outra conta do Piggu. Se não foi você, fale com a gente pelo canal de privacidade.",
                    HttpStatus.CONFLICT, "CPF_EM_USO");
        }
        conta.informarCpf(cofre.cifrar(cpf), indice);
        usuarios.save(conta);
        trilha.registrar(conta.getHouseholdId(), conta.getId(), TrilhaDeAuditoria.Acao.EDITOU, "conta",
                conta.getId(), null, "CPF informado");
        log.info("CPF informado: conta={}", conta.getId());
    }

    /**
     * O pais onde a pessoa mora e, se ela ja digitou, o CPF (pedido aqui e fora). Trocar
     * de pais nao apaga documento nenhum: o que ja foi informado fica preso a conta, e quem
     * ja tem um documento (o CPF ou o de outro pais, de conta antiga) nao precisa de outro.
     */
    @Transactional
    public UserResponse informarPais(UUID contaId, String paisDigitado, String documento) {
        UserAccount conta = conta(contaId);
        String pais = Paises.pais(paisDigitado);
        if (!Texto.vazio(documento) && !conta.temCpf() && !conta.temDocumento()) {
            prenderCpf(conta, conferirCpf(documento));
        }
        if (!pais.equals(conta.getPais())) {
            conta.declararPais(pais);
            usuarios.save(conta);
            trilha.registrar(conta.getHouseholdId(), conta.getId(), TrilhaDeAuditoria.Acao.EDITOU, "conta",
                    conta.getId(), null, "Pais onde mora: " + pais);
            log.info("Pais: conta={} pais={}", conta.getId(), pais);
        }
        return UserResponse.de(conta, familia.daConta(conta));
    }

    /**
     * Como a pessoa quer ser chamada e, se ainda nao informou, quando nasceu, o CPF e o
     * celular: o mesmo que o cadastro por senha pede (D-088). O nome muda quando quiser; o
     * resto entra uma vez (ver {@link UserAccount#informarNascimento}).
     */
    @Transactional
    public UserResponse sobreVoce(UUID contaId, String apelido, LocalDate nascimento, String cpf, String telefone) {
        UserAccount conta = conta(contaId);
        String nome = Texto.limitar(Texto.espacoUnico(apelido), 120);
        if (nome.isEmpty()) {
            throw new BusinessException("Conte como podemos te chamar.");
        }
        if (conta.getBirthDate() == null && nascimento == null) {
            throw new BusinessException("Informe sua data de nascimento.");
        }
        if (!conta.documentoEmDia() && Texto.vazio(cpf)) {
            throw new BusinessException("Informe seu CPF.", HttpStatus.UNPROCESSABLE_CONTENT, "CPF_NECESSARIO");
        }
        if (conta.getPhone() == null && Texto.vazio(telefone)) {
            throw new BusinessException("Informe seu celular.");
        }
        conta.setNickname(nome);
        if (conta.getBirthDate() == null) {
            conta.informarNascimento(conferirNascimento(nascimento));
        }
        if (conta.getPhone() == null) {
            conta.setPhone(AuthService.e164(telefone, conta.getPais()));
        }
        if (!conta.documentoEmDia()) {
            prenderCpf(conta, conferirCpf(cpf));
        }
        usuarios.save(conta);
        log.info("Sobre voce atualizado: conta={}", conta.getId());
        return UserResponse.de(conta, familia.daConta(conta));
    }

    /** Data plausivel e de quem tem pelo menos {@link UserAccount#IDADE_MINIMA} anos. */
    public static LocalDate conferirNascimento(LocalDate data) {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        if (data == null || data.isAfter(hoje) || data.isBefore(hoje.minusYears(120))) {
            throw new BusinessException("Confira a data de nascimento.");
        }
        if (data.isAfter(hoje.minusYears(UserAccount.IDADE_MINIMA))) {
            throw new BusinessException("O Piggu é para quem tem " + UserAccount.IDADE_MINIMA + " anos ou mais.",
                    HttpStatus.UNPROCESSABLE_CONTENT, "IDADE_MINIMA");
        }
        return data;
    }

    /**
     * Aceite de uma versao nova dos Termos e do Aviso por quem ja tem conta. So vale a
     * versao vigente: um front antigo nao colhe aceite sobre um texto que saiu do ar.
     */
    @Transactional
    public UserResponse aceitarTermos(UUID contaId, String versao) {
        if (!Consentimentos.VERSAO_DO_AVISO.equals(versao)) {
            throw new BusinessException("Os termos mudaram. Recarregue a pagina e leia de novo.",
                    HttpStatus.UNPROCESSABLE_CONTENT, Consentimentos.CODIGO_AVISO_MUDOU);
        }
        UserAccount conta = conta(contaId);
        conta.aceitarTermos(versao);
        usuarios.save(conta);
        trilha.registrar(conta.getHouseholdId(), conta.getId(), TrilhaDeAuditoria.Acao.EDITOU, "conta",
                conta.getId(), null, "Termos aceitos (versao " + versao + ")");
        log.info("Termos aceitos: conta={} versao={}", conta.getId(), versao);
        return UserResponse.de(conta, familia.daConta(conta));
    }

    /** Grava a foto escolhida pela pessoa, ja reduzida e sem metadados. */
    @Transactional
    public UserResponse trocarFoto(UUID contaId, String base64, String mimeType) {
        UserAccount conta = conta(contaId);
        conta.trocarFoto(FotoDePerfil.preparar(base64, mimeType));
        return fotoAlterada(conta, "Foto de perfil trocada");
    }

    /** Tira a foto: a propria e apagada e a do Google deixa de aparecer. */
    @Transactional
    public UserResponse removerFoto(UUID contaId) {
        UserAccount conta = conta(contaId);
        conta.removerFoto();
        return fotoAlterada(conta, "Foto de perfil removida");
    }

    /** Apaga a foto propria e volta a mostrar a da conta Google. */
    @Transactional
    public UserResponse usarFotoDoGoogle(UUID contaId) {
        UserAccount conta = conta(contaId);
        conta.usarFotoDoGoogle();
        return fotoAlterada(conta, "Foto do Google de volta no perfil");
    }

    private UserResponse fotoAlterada(UserAccount conta, String descricao) {
        usuarios.save(conta);
        trilha.registrar(conta.getHouseholdId(), conta.getId(), TrilhaDeAuditoria.Acao.EDITOU, "conta",
                conta.getId(), null, descricao);
        log.info("{}: conta={}", descricao, conta.getId());
        return UserResponse.de(conta, familia.daConta(conta));
    }

    /**
     * Exclui a conta. Primeiro os outros servicos (se algum falhar, a conta fica e a
     * pessoa tenta de novo), depois a assinatura na Stripe, por ultimo a linha daqui.
     */
    @Transactional
    public void excluir(UUID contaId) {
        UserAccount conta = conta(contaId);
        Optional<Household> vazia = familia.sairDeOndeEsta(conta);

        if (conta.getStripeCustomerId() != null) {
            if (pagamentos.habilitado()) {
                pagamentos.encerrarCliente(conta.getStripeCustomerId());
            } else {
                log.warn("Conta {} tinha cliente na Stripe, mas a Stripe esta desligada", conta.getId());
            }
        }

        // So o HMAC do CPF e a data do teste ficam: excluir e recriar a conta nao devolve o teste.
        testes.lembrarAoExcluir(conta);
        sessoes.apagarPorUsuario(conta.getId());
        usuarios.delete(conta);
        usuarios.flush();
        vazia.ifPresent(familias::delete);
        log.info("Conta excluida id={} familiaApagada={}", contaId, vazia.isPresent());
    }

    private UserAccount conta(UUID id) {
        return usuarios.findById(id)
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
    }
}
