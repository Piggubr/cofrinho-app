package com.piggu.finance.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.finance.api.dto.DepositRequest;
import com.piggu.finance.api.dto.DepositResponse;
import com.piggu.finance.api.dto.PiggyBankResponse;
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
 *   <li>Quem tem perfil FAMILIAR ve somente os proprios depositos, e o saldo dele e
 *       o proprio total, sem desconto de gastos.</li>
 * </ol>
 */
@Service
public class PiggyBankService {

    private final PiggyDepositRepository depositos;
    private final ExpenseRepository gastos;

    public PiggyBankService(PiggyDepositRepository depositos, ExpenseRepository gastos) {
        this.depositos = depositos;
        this.gastos = gastos;
    }

    @Transactional(readOnly = true)
    public PiggyBankResponse consultar(CurrentUser usuario) {
        if (usuario.role() == PigguRole.FAMILIAR) {
            return consultarComoFamiliar(usuario.email());
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
        PiggyDeposit deposito = new PiggyDeposit(data, pedido.valor(), emailUsuario);
        return DepositResponse.de(depositos.save(deposito));
    }

    @Transactional
    public void excluir(UUID id, CurrentUser usuario) {
        PiggyDeposit deposito = depositos.findById(id)
                .orElseThrow(() -> new NotFoundException("Deposito nao encontrado."));

        if (!usuario.podeGerenciar(deposito.getUserEmail())) {
            throw new ForbiddenException("Voce nao pode apagar este deposito.");
        }
        depositos.delete(deposito);
    }

    private PiggyBankResponse consultarComoFamiliar(String email) {
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
