package com.piggu.identity.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Politica de retencao do identity (LGPD art. 15 e 16), aplicada todo dia.
 *
 * <ul>
 *   <li>sessao vencida e convite vencido saem;</li>
 *   <li>conta sem nenhum acesso por 24 meses e excluida como se a pessoa tivesse
 *       pedido (cascata pelos servicos, ver {@link MinhaContaService#excluir});</li>
 *   <li>conta desativada ha 12 meses tambem.</li>
 * </ul>
 *
 * <p>Os prazos estao no aviso de privacidade; mudou aqui, muda la. Uma conta que falha
 * (servico fora do ar) fica para o dia seguinte, sem travar as outras.</p>
 *
 * <p>ponytail: com mais de uma replica do identity o job roda em cada uma; as exclusoes
 * sao idempotentes, entao o pior caso e trabalho repetido. Trava distribuida (ShedLock)
 * se isso pesar.</p>
 */
@Component
public class RetencaoDeDados {

    private static final Logger log = LoggerFactory.getLogger(RetencaoDeDados.class);

    private final RefreshSessionRepository sessoes;
    private final HouseholdInviteRepository convites;
    private final UserAccountRepository usuarios;
    private final MinhaContaService contas;
    private final TransactionTemplate transacao;
    private final Duration semAcesso;
    private final Duration desativada;

    public RetencaoDeDados(RefreshSessionRepository sessoes,
                           HouseholdInviteRepository convites,
                           UserAccountRepository usuarios,
                           MinhaContaService contas,
                           TransactionTemplate transacao,
                           @Value("${piggu.retencao.conta-sem-acesso:P730D}") Duration semAcesso,
                           @Value("${piggu.retencao.conta-desativada:P365D}") Duration desativada) {
        this.sessoes = sessoes;
        this.convites = convites;
        this.usuarios = usuarios;
        this.contas = contas;
        this.transacao = transacao;
        this.semAcesso = semAcesso;
        this.desativada = desativada;
    }

    @Scheduled(cron = "${piggu.retencao.cron:0 30 3 * * *}", zone = "America/Sao_Paulo")
    public void aplicar() {
        Instant agora = Instant.now();
        limparSessoesEConvites(agora);

        Set<UUID> vencidas = new LinkedHashSet<>(usuarios.semAcessoDesde(agora.minus(semAcesso)));
        vencidas.addAll(usuarios.desativadasDesde(agora.minus(desativada)));
        int excluidas = 0;
        for (UUID conta : vencidas) {
            try {
                contas.excluir(conta);
                excluidas++;
            } catch (RuntimeException erro) {
                log.warn("Retencao: conta {} fica para a proxima rodada", conta, erro);
            }
        }
        log.info("Retencao aplicada: contasExcluidas={} pendentes={}", excluidas, vencidas.size() - excluidas);
    }

    /** TransactionTemplate e nao @Transactional: chamada de dentro da classe nao passa pelo proxy. */
    private void limparSessoesEConvites(Instant agora) {
        transacao.executeWithoutResult(status -> {
            int sessoesApagadas = sessoes.apagarExpiradas(agora);
            int convitesApagados = convites.apagarVencidos(agora);
            log.info("Retencao: sessoes={} convites={} apagados", sessoesApagadas, convitesApagados);
        });
    }
}
