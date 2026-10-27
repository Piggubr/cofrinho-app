package com.piggu.identity.domain;

import com.piggu.common.security.Plano;
import com.piggu.identity.billing.Periodo;
import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.auditoria.TrilhaDeAuditoria.Acao;
import com.piggu.common.dados.Consentimentos;
import com.piggu.common.dados.EscopoDeExclusao;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.common.web.Meses;
import com.piggu.common.web.Texto;
import com.piggu.identity.api.dto.FamiliaResponse;
import com.piggu.identity.avisos.Aparelhos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Familias: quem entra em qual, convites e saida.
 *
 * <p>Cadastro aberto: qualquer conta nova cria familia, com a pessoa como titular; quem
 * foi convidado ve o convite e decide se entra. Mudar alguem de familia derruba as sessoes dele, porque a familia vai no
 * token e o token antigo continuaria apontando para a casa de antes.</p>
 */
@Service
public class FamiliaService {

    static final Duration VALIDADE_DO_CONVITE = Duration.ofDays(7);
    static final int MAXIMO_DE_CONVITES = 10;

    private static final Logger log = LoggerFactory.getLogger(FamiliaService.class);

    /** Entidade dos eventos de auditoria do identity: a pessoa na familia. */
    private static final String PESSOA = "pessoa";

    private final HouseholdRepository familias;
    private final HouseholdInviteRepository convites;
    private final UserAccountRepository usuarios;
    private final RefreshSessionRepository sessoes;
    private final CascataDeDados cascata;
    private final TokenService tokens;
    private final TrilhaDeAuditoria trilha;
    private final Aparelhos aparelhos;

    public FamiliaService(HouseholdRepository familias,
                          HouseholdInviteRepository convites,
                          UserAccountRepository usuarios,
                          RefreshSessionRepository sessoes,
                          CascataDeDados cascata,
                          TokenService tokens,
                          TrilhaDeAuditoria trilha,
                          Aparelhos aparelhos) {
        this.familias = familias;
        this.convites = convites;
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.cascata = cascata;
        this.tokens = tokens;
        this.trilha = trilha;
        this.aparelhos = aparelhos;
    }

    /**
     * Conta nova: entra na familia que convidou, ou ganha uma familia so dela. O aceite
     * dos termos e gravado aqui, no ato de criar a conta, ou a conta nao nasce.
     */
    @Transactional
    public UserAccount criarConta(String email, String primeiroNome, String versaoDosTermos) {
        return criarConta(email, primeiroNome, versaoDosTermos, true);
    }

    /**
     * Toda conta nova nasce na propria familia, mesmo convidada: o convite fica esperando em
     * {@link #convitesParaMim} e so vale se a pessoa aceitar ({@link #aceitarConvite}). Entrar
     * sozinha deixaria qualquer titular colocar um e-mail alheio na sua casa sem a pessoa saber.
     *
     * @param emailConfirmado o e-mail ja foi provado (login Google)
     */
    @Transactional
    public UserAccount criarConta(String email, String primeiroNome, String versaoDosTermos, boolean emailConfirmado) {
        if (versaoDosTermos == null) {
            throw new BusinessException("Para criar sua conta, leia e aceite os Termos de Uso e o Aviso de Privacidade.",
                    HttpStatus.UNPROCESSABLE_CONTENT, "TERMOS_NECESSARIOS");
        }
        if (!Consentimentos.VERSAO_DO_AVISO.equals(versaoDosTermos)) {
            throw new BusinessException("Os termos mudaram. Recarregue a pagina e leia de novo.",
                    HttpStatus.CONFLICT, Consentimentos.CODIGO_AVISO_MUDOU);
        }
        Household familia = familias.save(new Household(nomePadrao(primeiroNome)));
        UserAccount nova = new UserAccount(email, PigguRole.TITULAR, familia.getId());
        nova.aceitarTermos(versaoDosTermos);
        if (emailConfirmado) {
            nova.confirmarEmail();
        }
        nova = usuarios.save(nova);
        log.info("Conta e familia criadas id={} familia={}", nova.getId(), familia.getId());
        return nova;
    }

    /** Pessoas da casa mais convites ainda validos: o que o plano familia precisa comportar. */
    @Transactional(readOnly = true)
    public long pessoasNaCasa(UUID familiaId) {
        return usuarios.findByHouseholdIdOrderByCreatedAtAsc(familiaId).size()
                + convites.countByHouseholdIdAndExpiresAtAfter(familiaId, Instant.now());
    }

    @Transactional(readOnly = true)
    public Household daConta(UserAccount conta) {
        return familias.findById(conta.getHouseholdId())
                .orElseThrow(() -> new IllegalStateException("Conta sem familia: " + conta.getId()));
    }

    @Transactional(readOnly = true)
    public FamiliaResponse ver(CurrentUser usuario) {
        UserAccount conta = conta(usuario);
        Household familia = daConta(conta);
        List<UserAccount> membros = usuarios.findByHouseholdIdOrderByCreatedAtAsc(familia.getId());
        // Convites pendentes trazem e-mail de quem ainda nao entrou: so o titular ve.
        List<HouseholdInvite> pendentes = usuario.isTitular()
                ? convites.findByHouseholdIdOrderByCreatedAtDesc(familia.getId()).stream().filter(HouseholdInvite::valido).toList()
                : List.of();
        return FamiliaResponse.de(familia, membros, pendentes);
    }

    @Transactional
    public FamiliaResponse renomear(CurrentUser usuario, String nome) {
        exigirTitular(usuario);
        Household familia = daConta(conta(usuario));
        familia.renomear(Texto.limitar(Texto.espacoUnico(nome), 120));
        return ver(usuario);
    }

    /**
     * O dono provou o e-mail (Google ou link de nova senha) de uma conta cadastrada sem prova.
     * Quem cadastrou pode ter sido outra pessoa: sai o que ela deixou na conta
     * ({@link UserAccount#esquecerQuemCadastrou}), os aparelhos e as sessoes dela, e, se a casa
     * tem mais gente, a conta vai para uma familia so dela, levando o que e dela.
     */
    @Transactional
    public void devolverAoDono(UserAccount conta) {
        conta.esquecerQuemCadastrou();
        esquecerQuemMostravaTudoPara(usuarios.findByHouseholdIdOrderByCreatedAtAsc(conta.getHouseholdId()), conta.getId());
        if (usuarios.findByHouseholdIdOrderByCreatedAtAsc(conta.getHouseholdId()).size() > 1) {
            mudarParaFamiliaPropria(conta);
        }
        aparelhos.esquecerDoUsuario(conta.getId());
        sessoes.apagarPorUsuario(conta.getId());
        log.warn("Conta cadastrada sem prova do e-mail devolvida ao dono: conta={}", conta.getId());
    }

    @Transactional
    public FamiliaResponse convidar(CurrentUser usuario, String emailBruto) {
        exigirTitular(usuario);
        exigirEmailConfirmado(conta(usuario));
        String email = Texto.email(emailBruto);
        if (email.isEmpty() || !email.contains("@")) {
            throw new BusinessException("Digite um e-mail valido.");
        }
        boolean daCasa = usuarios.findByEmail(email)
                .filter(existente -> existente.getHouseholdId().equals(usuario.familia()))
                .isPresent();
        if (daCasa) {
            throw new BusinessException("Essa pessoa ja faz parte da familia.", HttpStatus.CONFLICT, "JA_E_DA_FAMILIA");
        }

        Instant agora = Instant.now();
        Instant vence = agora.plus(VALIDADE_DO_CONVITE);
        convites.findByHouseholdIdAndEmail(usuario.familia(), email).ifPresentOrElse(
                convite -> convite.renovar(vence),
                () -> {
                    // Anti-abuso: convite vira linha no banco e aparece para o e-mail convidado.
                    if (convites.countByHouseholdIdAndExpiresAtAfter(usuario.familia(), agora) >= MAXIMO_DE_CONVITES) {
                        throw new BusinessException("Ja ha " + MAXIMO_DE_CONVITES + " convites esperando resposta. "
                                + "Cancele algum para convidar outra pessoa.", HttpStatus.UNPROCESSABLE_CONTENT,
                                "LIMITE_DE_CONVITES");
                    }
                    // No plano familia a casa tem ate 5 pessoas: e quanto o Premium cobre.
                    Household casa = familias.findById(usuario.familia()).orElseThrow();
                    if (casa.isFamilia() && casa.planoVigente() == Plano.PREMIUM
                            && pessoasNaCasa(usuario.familia()) >= Periodo.PESSOAS_NO_FAMILIA) {
                        throw new BusinessException("O plano família cobre até " + Periodo.PESSOAS_NO_FAMILIA
                                + " pessoas, contando os convites pendentes.", HttpStatus.UNPROCESSABLE_CONTENT,
                                "LIMITE_DO_FAMILIA");
                    }
                    convites.save(new HouseholdInvite(usuario.familia(), email, usuario.id(), vence));
                });
        // Sem o e-mail no log: e o dado pessoal de quem ainda nem aceitou nada.
        log.info("Convite para a familia {} criado", usuario.familia());
        return ver(usuario);
    }

    @Transactional
    public FamiliaResponse cancelarConvite(CurrentUser usuario, UUID conviteId) {
        exigirTitular(usuario);
        HouseholdInvite convite = convites.findById(conviteId)
                .filter(encontrado -> encontrado.getHouseholdId().equals(usuario.familia()))
                .orElseThrow(() -> new NotFoundException("Convite nao encontrado."));
        convites.delete(convite);
        return ver(usuario);
    }

    /** Convites para o e-mail de quem ja tem conta, para a tela da familia oferecer. */
    @Transactional(readOnly = true)
    public List<FamiliaResponse.Convite> convitesParaMim(CurrentUser usuario) {
        // E-mail sem prova nao ve convite: o dono do e-mail pode ser outra pessoa.
        if (!usuarios.findById(usuario.id()).map(UserAccount::isEmailVerified).orElse(false)) {
            return List.of();
        }
        return convites.findByEmailOrderByCreatedAtDesc(usuario.email()).stream()
                .filter(HouseholdInvite::valido)
                .filter(convite -> !convite.getHouseholdId().equals(usuario.familia()))
                .map(convite -> new FamiliaResponse.Convite(convite.getId(), convite.getEmail(), convite.getExpiresAt(),
                        familias.findById(convite.getHouseholdId()).map(Household::getName).orElse("")))
                .toList();
    }

    /**
     * Quem ja tem conta aceita entrar em outra familia e leva o que e dela. Se era a ultima
     * da familia antiga, o resto de la sai; senao o que era da familia fica, anonimizado.
     */
    @Transactional
    public void aceitarConvite(CurrentUser usuario, UUID conviteId) {
        UserAccount conta = conta(usuario);
        HouseholdInvite convite = convites.findById(conviteId)
                .filter(encontrado -> encontrado.getEmail().equals(conta.getEmail()) && encontrado.valido())
                .orElseThrow(() -> new NotFoundException("Convite nao encontrado ou vencido."));
        exigirEmailConfirmado(conta);
        if (convite.getHouseholdId().equals(conta.getHouseholdId())) {
            throw new BusinessException("Voce ja faz parte dessa familia.");
        }
        // A renovacao vai para a familia atual de quem paga: na outra casa, trocaria o plano de la.
        Household atual = daConta(conta);
        if (conta.getId().equals(atual.getSubscriberId()) && atual.planoVigente() == Plano.PREMIUM) {
            throw new BusinessException("Você assina o Premium da sua família. Para entrar em outra, cancele a "
                    + "assinatura e aceite o convite depois que o Premium terminar.", HttpStatus.CONFLICT,
                    "ASSINATURA_ATIVA");
        }

        PigguRole papel = entraComoParceiro(conta, convite.getHouseholdId()) ? PigguRole.PARCEIRO : PigguRole.MEMBRO;
        Optional<Household> vazia = sairDeOndeEsta(conta, convite.getHouseholdId());
        conta.mudarDeFamilia(convite.getHouseholdId(), papel);
        usuarios.saveAndFlush(conta);
        trilha.registrar(convite.getHouseholdId(), conta.getId(), Acao.ENTROU, PESSOA, conta.getId(), null,
                pessoa(conta));
        vazia.ifPresent(familias::delete);
        convites.deleteAll(convites.findByEmailOrderByCreatedAtDesc(conta.getEmail()));
        sessoes.apagarPorUsuario(conta.getId());
        log.info("Convite aceito: conta={} familia={} papel={}", conta.getId(), convite.getHouseholdId(), papel);
    }

    /**
     * No plano casal, quem o titular convida e o casal (D-086): entra como parceiro se a casa
     * ainda nao tem um (nem convite de parceria esperando) e a pessoa nao e menor de idade.
     */
    private boolean entraComoParceiro(UserAccount conta, UUID destino) {
        Household casa = familias.findById(destino).orElseThrow();
        return casa.isCasal() && casa.planoVigente() == Plano.PREMIUM
                && !conta.menorDeIdade(LocalDate.now(Meses.BRASILIA))
                && usuarios.findByHouseholdIdOrderByCreatedAtAsc(destino).stream()
                        .noneMatch(outro -> outro.getRole() == PigguRole.PARCEIRO || outro.convidadoParaParceria());
    }

    /** Exclusao da conta: os dados da pessoa saem junto (ver {@link #sairDeOndeEsta(UserAccount, UUID)}). */
    @Transactional
    public Optional<Household> sairDeOndeEsta(UserAccount conta) {
        return sairDeOndeEsta(conta, null);
    }

    /**
     * Tira a pessoa da familia atual (nos outros servicos) e acerta a familia: se ninguem
     * fica, ela some; se o titular sai, o membro mais antigo assume.
     *
     * @param destino familia para onde vai o que e da pessoa (lancamentos, metas, fotos...);
     *                nulo quando a conta esta sendo excluida e os dados saem com ela
     * @return a familia que ficou vazia, para quem chamou apagar depois de tirar a conta
     *         dela (a conta aponta para a familia); vazio quando outras pessoas ficam
     */
    @Transactional
    public Optional<Household> sairDeOndeEsta(UserAccount conta, UUID destino) {
        Household familia = daConta(conta);
        List<UserAccount> outros = usuarios.findByHouseholdIdOrderByCreatedAtAsc(familia.getId()).stream()
                .filter(outro -> !outro.getId().equals(conta.getId()))
                .toList();
        EscopoDeExclusao escopo = outros.isEmpty() ? EscopoDeExclusao.FAMILIA : EscopoDeExclusao.PESSOA;
        esquecerQuemMostravaTudoPara(outros, conta.getId());

        if (destino != null) {
            cascata.apagar(tokens.gerarTokenDeMudanca(conta, familia, destino));
        }
        cascata.apagar(tokens.gerarTokenDeExclusao(conta, familia, escopo));
        trilha.esquecer(familia.getId(), conta.getId(), escopo == EscopoDeExclusao.FAMILIA);

        if (escopo == EscopoDeExclusao.FAMILIA) {
            convites.deleteAll(convites.findByHouseholdIdOrderByCreatedAtDesc(familia.getId()));
            return Optional.of(familia);
        }
        boolean semTitular = outros.stream().noneMatch(FamiliaService::cuidaDaFamilia);
        if (semTitular) {
            // O parceiro mais antigo assume; sem parceiro, o membro mais antigo.
            UserAccount novoTitular = outros.stream()
                    .filter(outro -> outro.getRole() == PigguRole.PARCEIRO)
                    .findFirst()
                    .orElse(outros.get(0));
            String antes = pessoa(novoTitular);
            novoTitular.setRole(PigguRole.TITULAR);
            trilha.registrar(familia.getId(), TrilhaDeAuditoria.SISTEMA, Acao.MUDOU_PAPEL, PESSOA, novoTitular.getId(),
                    antes, pessoa(novoTitular));
            usuarios.save(novoTitular);
            sessoes.apagarPorUsuario(novoTitular.getId());
            log.info("Titularidade passada: familia={} novoTitular={}", familia.getId(), novoTitular.getId());
        }
        return Optional.empty();
    }

    /**
     * O titular tira alguem da familia. A pessoa vai para uma familia so dela e leva o que
     * lancou; a familia perde o acesso a isso.
     */
    @Transactional
    public FamiliaResponse removerMembro(CurrentUser usuario, UUID membroId) {
        exigirTitular(usuario);
        if (membroId.equals(usuario.id())) {
            throw new BusinessException("Para sair da familia, use a opcao Sair.");
        }
        UserAccount membro = usuarios.findById(membroId)
                .filter(encontrado -> encontrado.getHouseholdId().equals(usuario.familia()))
                .orElseThrow(() -> new NotFoundException("Pessoa nao encontrada na familia."));
        if (cuidaDaFamilia(membro)) {
            throw new ForbiddenException("So da para remover membros e parceiros.");
        }
        trilha.registrar(usuario.familia(), usuario.id(), Acao.REMOVEU, PESSOA, membroId, pessoa(membro), null);
        mudarParaFamiliaPropria(membro);
        log.info("Membro removido da familia: conta={} familia={}", membroId, usuario.familia());
        return ver(usuario);
    }

    /**
     * O titular convida um membro para ser parceiro, ou volta o parceiro (ou o convite) a
     * membro. Parceiro e titular podem ver os lancamentos um do outro, por isso o papel so muda
     * quando a pessoa aceita ({@link #aceitarParceria}); voltar a membro vale na hora, porque
     * so diminui o que cada um ve. Um parceiro por familia: o casal.
     */
    @Transactional
    public FamiliaResponse mudarPapel(CurrentUser usuario, UUID membroId, PigguRole papel) {
        exigirTitular(usuario);
        if (papel != PigguRole.PARCEIRO && papel != PigguRole.MEMBRO) {
            throw new BusinessException("O papel deve ser parceiro ou membro.");
        }
        UserAccount membro = usuarios.findById(membroId)
                .filter(encontrado -> encontrado.getHouseholdId().equals(usuario.familia()))
                .orElseThrow(() -> new NotFoundException("Pessoa nao encontrada na familia."));
        if (cuidaDaFamilia(membro)) {
            throw new ForbiddenException("O papel do titular nao muda por aqui.");
        }
        if (papel == PigguRole.PARCEIRO) {
            exigirEmailConfirmado(conta(usuario));
            convidarParaParceria(usuario, membro);
        } else if (membro.convidadoParaParceria()) {
            membro.encerrarConviteDeParceria();
            usuarios.save(membro);
            log.info("Convite de parceria cancelado: conta={} familia={}", membroId, usuario.familia());
        } else if (membro.getRole() == PigguRole.PARCEIRO) {
            String antes = pessoa(membro);
            membro.setRole(PigguRole.MEMBRO);
            // Deixou de ser o casal: nenhum dos dois continua vendo tudo do outro.
            membro.mostrarTudoPara(null);
            esquecerQuemMostravaTudoPara(usuarios.findByHouseholdIdOrderByCreatedAtAsc(usuario.familia()), membroId);
            trilha.registrar(usuario.familia(), usuario.id(), Acao.MUDOU_PAPEL, PESSOA, membroId, antes, pessoa(membro));
            usuarios.save(membro);
            sessoes.apagarPorUsuario(membro.getId());
            log.info("Parceiro voltou a membro: conta={} familia={}", membroId, usuario.familia());
        }
        return ver(usuario);
    }

    private void convidarParaParceria(CurrentUser usuario, UserAccount membro) {
        if (membro.getRole() == PigguRole.PARCEIRO || membro.convidadoParaParceria()) {
            return;
        }
        boolean outroParceiro = usuarios.findByHouseholdIdOrderByCreatedAtAsc(usuario.familia()).stream()
                .anyMatch(outro -> !outro.getId().equals(membro.getId())
                        && (outro.getRole() == PigguRole.PARCEIRO || outro.convidadoParaParceria()));
        if (outroParceiro) {
            throw new BusinessException("A familia ja tem um parceiro (ou um convite esperando resposta). "
                    + "Volte essa pessoa a membro antes de convidar outra.", HttpStatus.CONFLICT, "JA_TEM_PARCEIRO");
        }
        membro.convidarParaParceria(Instant.now());
        usuarios.save(membro);
        trilha.registrar(usuario.familia(), usuario.id(), Acao.CONVIDOU, PESSOA, membro.getId(), null,
                membro.primeiroNomeExibicao() + " · PARCEIRO");
        log.info("Convite de parceria: conta={} familia={}", membro.getId(), usuario.familia());
    }

    /**
     * A pessoa aceita ser parceira: dali em diante ela e o titular veem os lancamentos dos
     * menores, o que o outro compartilhar e, se o outro escolher no perfil, tudo o que ele
     * lanca. Entra de novo para o papel valer no token.
     */
    @Transactional
    public void aceitarParceria(CurrentUser usuario) {
        UserAccount conta = conta(usuario);
        if (!conta.convidadoParaParceria() || conta.getRole() != PigguRole.MEMBRO) {
            throw new NotFoundException("Nenhum convite de parceria esperando resposta.");
        }
        String antes = pessoa(conta);
        conta.encerrarConviteDeParceria();
        conta.setRole(PigguRole.PARCEIRO);
        usuarios.save(conta);
        trilha.registrar(conta.getHouseholdId(), conta.getId(), Acao.MUDOU_PAPEL, PESSOA, conta.getId(), antes, pessoa(conta));
        sessoes.apagarPorUsuario(conta.getId());
        log.info("Parceria aceita: conta={} familia={}", conta.getId(), conta.getHouseholdId());
    }

    /** A pessoa recusa: continua membro e o convite sai. */
    @Transactional
    public FamiliaResponse recusarParceria(CurrentUser usuario) {
        UserAccount conta = conta(usuario);
        if (conta.convidadoParaParceria()) {
            conta.encerrarConviteDeParceria();
            usuarios.save(conta);
            log.info("Parceria recusada: conta={} familia={}", conta.getId(), conta.getHouseholdId());
        }
        return ver(usuario);
    }

    /** O membro (ou parceiro) sai por conta propria, com o que e dele, para uma familia so dele. */
    @Transactional
    public void sair(CurrentUser usuario) {
        UserAccount conta = conta(usuario);
        if (cuidaDaFamilia(conta)) {
            throw new BusinessException("O titular nao sai da propria familia.");
        }
        UUID antiga = conta.getHouseholdId();
        String quem = pessoa(conta);
        mudarParaFamiliaPropria(conta);
        // Depois de esquecer (dentro da mudanca): a saida fica na trilha da familia.
        trilha.registrar(antiga, conta.getId(), Acao.SAIU, PESSOA, conta.getId(), quem, null);

        log.info("Membro saiu da familia: conta={}", conta.getId());
    }

    /**
     * Cria a familia nova antes de sair: os dados da pessoa precisam de um lugar para ir. O id
     * sai da conta e da familia de onde ela sai, e nao do acaso: se um servico falha no meio, o
     * identity desfaz tudo, mas quem ja moveu os dados nao desfaz; a nova tentativa precisa
     * mandar para o mesmo lugar. Um id ja usado por uma saida anterior que deu certo passa ao
     * proximo da sequencia, que tambem se repete numa nova tentativa.
     */
    private void mudarParaFamiliaPropria(UserAccount conta) {
        UUID id;
        int vez = 0;
        do {
            id = UUID.nameUUIDFromBytes(("propria:" + conta.getId() + ":" + conta.getHouseholdId() + ":" + vez++)
                    .getBytes(StandardCharsets.UTF_8));
        } while (familias.existsById(id));
        Household nova = familias.save(new Household(id, nomePadrao(conta.primeiroNomeExibicao())));
        sairDeOndeEsta(conta, nova.getId());
        conta.mudarDeFamilia(nova.getId(), PigguRole.TITULAR);

        usuarios.save(conta);
        sessoes.apagarPorUsuario(conta.getId());
    }

    /** Quem mostrava tudo para {@code pessoa} volta a mostrar so o compartilhado. */
    private void esquecerQuemMostravaTudoPara(List<UserAccount> contas, UUID pessoa) {
        contas.stream().filter(outra -> outra.mostraTudoPara(pessoa)).forEach(outra -> {
            outra.mostrarTudoPara(null);
            usuarios.save(outra);
        });
    }

    private UserAccount conta(CurrentUser usuario) {
        return usuarios.findById(usuario.id())
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
    }

    /** "Bia · PARCEIRO": o primeiro nome basta para a familia saber quem e. */
    private static String pessoa(UserAccount conta) {
        return conta.primeiroNomeExibicao() + " · " + conta.getRole().name();
    }

    /** Titular ou ADMIN: quem responde pela familia e nao sai nem e removido dela. */
    private static boolean cuidaDaFamilia(UserAccount conta) {
        return conta.getRole() == PigguRole.TITULAR || conta.getRole() == PigguRole.ADMIN;
    }

    private static void exigirTitular(CurrentUser usuario) {
        if (!usuario.isTitular()) {
            throw new ForbiddenException("So o titular cuida da familia.");
        }
    }

    /**
     * Dar acesso a familia (convite, parceria, mostrar tudo, aparelho de push) so com o e-mail
     * provado: senao quem cadastrou o e-mail de outra pessoa arma a conta antes de o dono chegar.
     */
    static void exigirEmailConfirmado(UserAccount conta) {
        if (!conta.isEmailVerified()) {
            throw new BusinessException("Confirme seu e-mail pelo link que enviamos (ou entre uma vez com o Google"
                    + " desse e-mail) para fazer isso.", HttpStatus.FORBIDDEN, "EMAIL_NAO_CONFIRMADO");
        }
    }

    private static String nomePadrao(String primeiroNome) {
        return Texto.vazio(primeiroNome) ? "Minha familia" : Texto.limitar("Familia de " + primeiroNome.trim(), 120);
    }
}
