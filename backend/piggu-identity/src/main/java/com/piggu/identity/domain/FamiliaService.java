package com.piggu.identity.domain;

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

    private static final Logger log = LoggerFactory.getLogger(FamiliaService.class);

    private final HouseholdRepository familias;
    private final HouseholdInviteRepository convites;
    private final UserAccountRepository usuarios;
    private final RefreshSessionRepository sessoes;

    public FamiliaService(HouseholdRepository familias,
                          HouseholdInviteRepository convites,
                          UserAccountRepository usuarios,
                          RefreshSessionRepository sessoes) {
        this.familias = familias;
        this.convites = convites;
        this.usuarios = usuarios;
        this.sessoes = sessoes;
    }

    /** Conta nova: entra na familia que convidou, ou ganha uma familia so dela. */
    @Transactional
    public UserAccount criarConta(String email, String primeiroNome) {
        Optional<HouseholdInvite> convite = convites.findByEmailOrderByCreatedAtDesc(email).stream()
                .filter(HouseholdInvite::valido)
                .findFirst();

        UserAccount nova;
        if (convite.isPresent()) {
            nova = usuarios.save(new UserAccount(email, PigguRole.MEMBRO, convite.get().getHouseholdId()));
            convites.deleteAll(convites.findByEmailOrderByCreatedAtDesc(email));
            log.info("Conta criada por convite id={} familia={}", nova.getId(), nova.getHouseholdId());
        } else {
            Household familia = familias.save(new Household(nomePadrao(primeiroNome)));
            nova = usuarios.save(new UserAccount(email, PigguRole.TITULAR, familia.getId()));
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
        Optional<UserAccount> existente = usuarios.findByEmail(email);
        if (existente.isPresent()) {
            boolean daCasa = existente.get().getHouseholdId().equals(usuario.familia());
            // ponytail: quem ja tem conta nao muda de familia por convite ainda; a mudanca
            // precisa apagar os dados da familia antiga, o que chega com a exclusao de conta.
            throw new BusinessException(daCasa
                    ? "Essa pessoa ja faz parte da familia."
                    : "Essa pessoa ja tem conta no Piggu. Por enquanto so da para convidar quem ainda nao entrou.",
                    HttpStatus.CONFLICT, "JA_TEM_CONTA");
        }

        Instant vence = Instant.now().plus(VALIDADE_DO_CONVITE);
        convites.findByHouseholdIdAndEmail(usuario.familia(), email).ifPresentOrElse(
                convite -> convite.renovar(vence),
                () -> convites.save(new HouseholdInvite(usuario.familia(), email, usuario.id(), vence)));
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
        if (membro.getRole() != PigguRole.MEMBRO) {
            throw new ForbiddenException("So da para remover membros.");
        }
        mudarParaFamiliaPropria(membro);
        log.info("Membro removido da familia: conta={} familia={}", membroId, usuario.familia());
        return ver(usuario);
    }

    /** O membro sai por conta propria e passa a ter uma familia so dele. */
    @Transactional
    public void sair(CurrentUser usuario) {
        UserAccount conta = conta(usuario);
        if (conta.getRole() != PigguRole.MEMBRO) {
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

    private static void exigirTitular(CurrentUser usuario) {
        if (!usuario.isTitular()) {
            throw new ForbiddenException("So o titular cuida da familia.");
        }
    }

    private static String nomePadrao(String primeiroNome) {
        return Texto.vazio(primeiroNome) ? "Minha familia" : Texto.limitar("Familia de " + primeiroNome.trim(), 120);
    }
}
