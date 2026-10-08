package com.piggu.identity.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.billing.ProvedorDePagamento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public MinhaContaService(UserAccountRepository usuarios,
                             RefreshSessionRepository sessoes,
                             HouseholdRepository familias,
                             FamiliaService familia,
                             CascataDeDados cascata,
                             ProvedorDePagamento pagamentos) {
        this.usuarios = usuarios;
        this.sessoes = sessoes;
        this.familias = familias;
        this.familia = familia;
        this.cascata = cascata;
        this.pagamentos = pagamentos;
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
        arquivo.put("familia", familia.ver(usuario));
        arquivo.put("sessoes", sessoes.findByUserId(conta.getId()).stream()
                .map(sessao -> Map.of("criadaEm", sessao.getCreatedAt(), "venceEm", sessao.getExpiresAt(),
                        "navegador", Optional.ofNullable(sessao.getUserAgent()).orElse("")))
                .toList());
        Map<String, JsonNode> servicos = cascata.exportar(token);
        arquivo.put("dados", servicos);
        log.info("Dados exportados: conta={} servicos={}", conta.getId(), servicos.keySet());
        return arquivo;
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
