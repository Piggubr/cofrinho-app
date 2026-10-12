package com.piggu.identity.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.billing.ProvedorDePagamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
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
    private final ProvedorDePagamento pagamentos;
    private final TrilhaDeAuditoria trilha;
    private final CofreDeCpf cofre;

    public static final String CODIGO_CPF_EM_USO = "CPF_EM_USO";

    public MinhaContaService(UserAccountRepository usuarios,
                             RefreshSessionRepository sessoes,
                             HouseholdRepository familias,
                             FamiliaService familia,
                             CascataDeDados cascata,
                             ProvedorDePagamento pagamentos,
                             TrilhaDeAuditoria trilha,
                             CofreDeCpf cofre) {
        this.cofre = cofre;
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
        if (conta.temCpf()) {
            arquivo.put("cpf", Map.of("numero", cofre.decifrar(conta.getCpfCifrado()).formatado(),
                    "informadoEm", conta.getCpfInformadoEm()));
        }
        arquivo.put("familia", familia.ver(usuario));
        if (conta.getTermsVersion() != null) {
            arquivo.put("aceiteDosTermos", Map.of("versao", conta.getTermsVersion(), "em", conta.getTermsAcceptedAt()));
        }
        arquivo.put("sessoes", sessoes.findByUserId(conta.getId()).stream()
                .map(sessao -> Map.of("criadaEm", sessao.getCreatedAt(), "venceEm", sessao.getExpiresAt(),
                        "navegador", Optional.ofNullable(sessao.getUserAgent()).orElse("")))
                .toList());
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
     * Prende o CPF a conta. Um CPF so pode estar em uma conta: e o que impede a mesma
     * pessoa de abrir varias. Depois de informado nao muda pelo app (correcao pelo canal
     * de privacidade), senao trocar de CPF liberaria o antigo para outra conta.
     */
    @Transactional
    public UserResponse informarCpf(UUID contaId, String digitado) {
        UserAccount conta = conta(contaId);
        if (conta.temCpf()) {
            throw new BusinessException("Sua conta já tem um CPF. Para corrigir, escreva para o canal de privacidade.",
                    HttpStatus.CONFLICT);
        }
        Cpf cpf = Cpf.de(digitado);
        String indice = cofre.indice(cpf);
        if (usuarios.existsByCpfIndice(indice)) {
            throw cpfEmUso();
        }
        conta.informarCpf(cofre.cifrar(cpf), indice);
        try {
            usuarios.saveAndFlush(conta);
        } catch (DataIntegrityViolationException corrida) {
            // Duas contas informando o mesmo CPF ao mesmo tempo: o indice unico decide.
            throw cpfEmUso();
        }
        trilha.registrar(conta.getHouseholdId(), conta.getId(), TrilhaDeAuditoria.Acao.EDITOU, "conta",
                conta.getId(), null, "CPF informado");
        log.info("CPF informado: conta={}", conta.getId());
        return UserResponse.de(conta, familia.daConta(conta));
    }

    private static BusinessException cpfEmUso() {
        return new BusinessException("Este CPF já está ligado a outra conta do Piggu. Entre com a conta que você "
                + "já tem; se não foi você quem a criou, escreva para o canal de privacidade.",
                HttpStatus.CONFLICT, CODIGO_CPF_EM_USO);
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
