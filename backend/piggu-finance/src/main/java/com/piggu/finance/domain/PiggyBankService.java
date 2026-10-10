package com.piggu.finance.domain;

import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.finance.api.dto.DepositRequest;
import com.piggu.finance.api.dto.DepositResponse;
import com.piggu.finance.api.dto.PiggyBankResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cofrinho: depositos e saldo.
 *
 * <p>Porte de carregarCofrinho_, carregarDadosFamiliar_, adicionarDeposito_ e
 * excluirDeposito_.</p>
 *
 * <p>Duas regras herdadas, ambas nao obvias e por isso preservadas com cuidado:</p>
 * <ol>
 *   <li>O saldo desconta apenas os gastos <em>registrados depois</em> do primeiro
 *       deposito. Gastos anteriores ao cofrinho nao pertencem a ele.</li>
 *   <li>Quem tem perfil MEMBRO ve somente os proprios depositos, e o saldo dele e
 *       o proprio total, sem desconto de gastos.</li>
 * </ol>
 */
@Service
public class PiggyBankService {

    private static final Logger log = LoggerFactory.getLogger(PiggyBankService.class);

    private final PiggyDepositRepository depositos;
    private final ExpenseRepository gastos;
    private final TrilhaDeAuditoria trilha;

    public PiggyBankService(PiggyDepositRepository depositos, ExpenseRepository gastos, TrilhaDeAuditoria trilha) {
        this.depositos = depositos;
        this.gastos = gastos;
        this.trilha = trilha;
    }

    @Transactional(readOnly = true)
    public PiggyBankResponse consultar(CurrentUser usuario) {
        if (usuario.role() == PigguRole.MEMBRO) {
            return consultarComoMembro(usuario.email());
        }

        List<DepositResponse> lista = depositos.findAllByOrderByDepositDateDesc().stream()
                .map(DepositResponse::de)
                .toList();
        BigDecimal totalDepositado = depositos.somarTudo();
        BigDecimal totalGasto = totalGastoDesdeOPrimeiroDeposito();

        return new PiggyBankResponse(
                lista,
                totalDepositado,
                totalGasto,
                totalDepositado.subtract(totalGasto)
        );
    }

    @Transactional
    public DepositResponse depositar(DepositRequest pedido, String emailUsuario) {
        LocalDate data = pedido.data() == null ? LocalDate.now() : pedido.data();
        PiggyDeposit deposito = depositos.save(new PiggyDeposit(data, pedido.valor(), emailUsuario));
        trilha.criou("deposito", deposito.getId(), Resumos.deposito(deposito));
        log.info("Deposito no cofrinho: id={} data={}", deposito.getId(), data);
        return DepositResponse.de(deposito);
    }

    @Transactional
    public void excluir(UUID id, CurrentUser usuario) {
        PiggyDeposit deposito = depositos.findById(id)
                .orElseThrow(() -> new NotFoundException("Deposito nao encontrado."));

        if (!usuario.podeGerenciar(deposito.getUserEmail())) {
            log.warn("Tentativa de apagar deposito alheio recusada: id={}", id);
            throw new ForbiddenException("Voce nao pode apagar este deposito.");
        }
        depositos.delete(deposito);
        trilha.apagou("deposito", id, Resumos.deposito(deposito));
        log.info("Deposito apagado: id={}", id);
    }

    private PiggyBankResponse consultarComoMembro(String email) {
        List<DepositResponse> proprios = depositos.findByUserEmailOrderByDepositDateDesc(email).stream()
                .map(DepositResponse::de)
                .toList();
        BigDecimal total = depositos.somarDoUsuario(email);
        return new PiggyBankResponse(proprios, total, BigDecimal.ZERO, total);
    }

    private BigDecimal totalGastoDesdeOPrimeiroDeposito() {
        Optional<Instant> inicio = depositos.primeiroDeposito();
        return inicio.map(gastos::somarDesde).orElse(BigDecimal.ZERO);
    }
}
