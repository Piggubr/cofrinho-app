package com.piggu.identity.domain;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.dados.EscopoDeExclusao;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.common.web.Texto;
import com.piggu.identity.api.dto.FamiliaResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Familias: quem entra em qual, convites e saida.
 *
 * <p>Cadastro aberto: qualquer login Google novo cria conta e familia, com a pessoa
 * como titular. A excecao e quem foi convidado: entra como membro da familia que
 * convidou. Mudar alguem de familia derruba as sessoes dele, porque a familia vai no
 * token e o token antigo continuaria apontando para a casa de antes.</p>
 */
@Service
public class FamiliaService {

    static final Duration VALIDADE_DO_CONVITE = Duration.ofDays(7);
    static final int MAXIMO_DE_CONVITES = 10;

    private static final Logger log = LoggerFactory.getLogger(FamiliaService.class);

    private final HouseholdRepository familias;
    private final HouseholdInviteRepository convites;
    private final UserAccountRepository usuarios;
    private final RefreshSessionRepository sessoes;
    private final CascataDeDados cascata;
    private final TokenService tokens;

    public FamiliaService(HouseholdRepository familias,
                          HouseholdInviteRepository convites,
                          UserAccountRepository usuarios,
                          RefreshSessionRepository sessoes,
                          CascataDeDados cascata,
                          TokenService tokens) {
        this.familias = familias;
        this.convites = convites;
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.cascata = cascata;
        this.tokens = tokens;
    }

    /**
     * Conta nova: entra na familia que convidou, ou ganha uma familia so dela. O aceite
     * dos termos e gravado aqui, no ato de criar a conta, ou a conta nao nasce.
     */
    @Transactional
    public UserAccount criarConta(String email, String primeiroNome, String versaoDosTermos) {
        if (versaoDosTermos == null) {
            throw new BusinessException("Para criar sua conta, leia e aceite os Termos de Uso e o Aviso de Privacidade.",
                    HttpStatus.UNPROCESSABLE_CONTENT, "TERMOS_NECESSARIOS");
        }
        if (!Consentimentos.VERSAO_DO_AVISO.equals(versaoDosTermos)) {
            throw new BusinessException("Os termos mudaram. Recarregue a pagina e leia de novo.",
                    HttpStatus.CONFLICT, Consentimentos.CODIGO_AVISO_MUDOU);
        }
        Optional<HouseholdInvite> convite = convites.findByEmailOrderByCreatedAtDesc(email).stream()
                .filter(HouseholdInvite::valido)
                .findFirst();

        UserAccount nova;
        if (convite.isPresent()) {
            nova = new UserAccount(email, PigguRole.MEMBRO, convite.get().getHouseholdId());
            nova.aceitarTermos(versaoDosTermos);
            nova = usuarios.save(nova);
            convites.deleteAll(convites.findByEmailOrderByCreatedAtDesc(email));
            log.info("Conta criada por convite id={} familia={}", nova.getId(), nova.getHouseholdId());
        } else {
            Household familia = familias.save(new Household(nomePadrao(primeiroNome)));
            nova = new UserAccount(email, PigguRole.TITULAR, familia.getId());
            nova.aceitarTermos(versaoDosTermos);
            nova = usuarios.save(nova);
            log.info("Conta e familia criadas id={} familia={}", nova.getId(), familia.getId());
        }
        return nova;
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

    @Transactional
    public FamiliaResponse convidar(CurrentUser usuario, String emailBruto) {
        exigirTitular(usuario);
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
        return convites.findByEmailOrderByCreatedAtDesc(usuario.email()).stream()
                .filter(HouseholdInvite::valido)
                .filter(convite -> !convite.getHouseholdId().equals(usuario.familia()))
                .map(convite -> new FamiliaResponse.Convite(convite.getId(), convite.getEmail(), convite.getExpiresAt(),
                        familias.findById(convite.getHouseholdId()).map(Household::getName).orElse("")))
                .toList();
    }

    /**
     * Quem ja tem conta aceita entrar em outra familia. Os dados da familia antiga saem
     * como numa exclusao de conta: se a pessoa era a ultima de la, tudo sai; senao o que
     * era compartilhado fica com quem ficou, anonimizado.
     */
    @Transactional
    public void aceitarConvite(CurrentUser usuario, UUID conviteId) {
        UserAccount conta = conta(usuario);
        HouseholdInvite convite = convites.findById(conviteId)
                .filter(encontrado -> encontrado.getEmail().equals(conta.getEmail()) && encontrado.valido())
                .orElseThrow(() -> new NotFoundException("Convite nao encontrado ou vencido."));
        if (convite.getHouseholdId().equals(conta.getHouseholdId())) {
            throw new BusinessException("Voce ja faz parte dessa familia.");
        }

        Optional<Household> vazia = sairDeOndeEsta(conta);
        conta.mudarDeFamilia(convite.getHouseholdId(), PigguRole.MEMBRO);
        usuarios.saveAndFlush(conta);
        vazia.ifPresent(familias::delete);
        convites.deleteAll(convites.findByEmailOrderByCreatedAtDesc(conta.getEmail()));
        sessoes.apagarPorUsuario(conta.getId());
        log.info("Convite aceito: conta={} familia={}", conta.getId(), convite.getHouseholdId());
    }

    /**
     * Apaga os dados da pessoa na familia atual (nos outros servicos) e acerta a familia:
     * se ninguem fica, ela some; se o titular sai, o membro mais antigo assume.
     *
     * @return a familia que ficou vazia, para quem chamou apagar depois de tirar a conta
     *         dela (a conta aponta para a familia); vazio quando outras pessoas ficam
     */
    @Transactional
    public Optional<Household> sairDeOndeEsta(UserAccount conta) {
        Household familia = daConta(conta);
        List<UserAccount> outros = usuarios.findByHouseholdIdOrderByCreatedAtAsc(familia.getId()).stream()
                .filter(outro -> !outro.getId().equals(conta.getId()))
                .toList();
        EscopoDeExclusao escopo = outros.isEmpty() ? EscopoDeExclusao.FAMILIA : EscopoDeExclusao.PESSOA;

        cascata.apagar(tokens.gerarTokenDeExclusao(conta, familia, escopo));

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
            novoTitular.setRole(PigguRole.TITULAR);
            usuarios.save(novoTitular);
            sessoes.apagarPorUsuario(novoTitular.getId());
            log.info("Titularidade passada: familia={} novoTitular={}", familia.getId(), novoTitular.getId());
        }
        return Optional.empty();
    }

    /** O titular tira alguem da familia; o que a pessoa lancou fica com a familia. */
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
        mudarParaFamiliaPropria(membro);
        log.info("Membro removido da familia: conta={} familia={}", membroId, usuario.familia());
        return ver(usuario);
    }

    /**
     * O titular promove um membro a parceiro, ou volta o parceiro a membro. A pessoa
     * entra de novo para o papel novo valer no token.
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
        if (membro.getRole() != papel) {
            membro.setRole(papel);
            usuarios.save(membro);
            sessoes.apagarPorUsuario(membro.getId());
            log.info("Papel alterado na familia: conta={} familia={} papel={}", membroId, usuario.familia(), papel);
        }
        return ver(usuario);
    }

    /** O membro (ou parceiro) sai por conta propria e passa a ter uma familia so dele. */
    @Transactional
    public void sair(CurrentUser usuario) {
        UserAccount conta = conta(usuario);
        if (cuidaDaFamilia(conta)) {
            throw new BusinessException("O titular nao sai da propria familia.");
        }
        mudarParaFamiliaPropria(conta);
        log.info("Membro saiu da familia: conta={}", conta.getId());
    }

    private void mudarParaFamiliaPropria(UserAccount conta) {
        Household nova = familias.save(new Household(nomePadrao(conta.primeiroNomeExibicao())));
        conta.mudarDeFamilia(nova.getId(), PigguRole.TITULAR);
        usuarios.save(conta);
        sessoes.apagarPorUsuario(conta.getId());
    }

    private UserAccount conta(CurrentUser usuario) {
        return usuarios.findById(usuario.id())
                .orElseThrow(() -> new UnauthorizedException("Conta nao encontrada. Entre novamente."));
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

    private static String nomePadrao(String primeiroNome) {
        return Texto.vazio(primeiroNome) ? "Minha familia" : Texto.limitar("Familia de " + primeiroNome.trim(), 120);
    }
}
