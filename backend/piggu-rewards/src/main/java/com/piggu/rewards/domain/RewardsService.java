package com.piggu.rewards.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.web.Texto;
import com.piggu.rewards.api.dto.CoinAdjustRequest;
import com.piggu.rewards.api.dto.CoinBalanceResponse;
import com.piggu.rewards.api.dto.PrizeRequest;
import com.piggu.rewards.api.dto.PrizeResponse;
import com.piggu.rewards.api.dto.RedemptionResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Fofocoins, premios e resgates.
 *
 * <p>Porte de carregarFofocoins_, ajustarFofocoins_, carregarPremios_, salvarPremio_
 * e resgatarPremio_.</p>
 *
 * <p>Ajuste e resgate rodam em isolamento SERIALIZABLE. O Apps Script conseguia o
 * mesmo efeito com LockService, que serializava o script inteiro. Aqui a garantia e
 * mais estreita e mais barata: duas operacoes simultaneas nao conseguem ler o mesmo
 * saldo e gastar as mesmas moedas duas vezes, e nada mais fica bloqueado.</p>
 */
@Service
public class RewardsService {

    private static final int HISTORICO_EXIBIDO = 100;

    private final CoinEntryRepository lancamentos;
    private final PrizeRepository premios;
    private final RedemptionRepository resgates;

    public RewardsService(CoinEntryRepository lancamentos,
                          PrizeRepository premios,
                          RedemptionRepository resgates) {
        this.lancamentos = lancamentos;
        this.premios = premios;
        this.resgates = resgates;
    }

    @Transactional(readOnly = true)
    public CoinBalanceResponse saldo() {
        List<CoinBalanceResponse.Movimento> historico =
                lancamentos.findAllByOrderByCreatedAtDesc(PageRequest.of(0, HISTORICO_EXIBIDO)).stream()
                        .map(CoinBalanceResponse.Movimento::de)
                        .toList();
        return new CoinBalanceResponse(lancamentos.saldo(), historico);
    }

    /**
     * Credita ou debita Fofocoins.
     *
     * <p>O saldo nao pode ficar negativo: e a unica regra que impede o ajuste manual
     * de quebrar a contabilidade do resgate.</p>
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CoinBalanceResponse ajustar(CoinAdjustRequest pedido, String emailResponsavel) {
        int valor = pedido.valor();
        if (valor == 0) {
            throw new BusinessException("Digite uma quantidade valida de Fofocoins.");
        }

        int atual = lancamentos.saldo();
        if (atual + valor < 0) {
            throw new BusinessException("O saldo nao pode ficar negativo.");
        }

        lancamentos.save(new CoinEntry(valor, Texto.limitar(pedido.motivo(), 200), emailResponsavel));
        return saldo();
    }

    @Transactional(readOnly = true)
    public List<PrizeResponse> listarPremios(boolean apenasAtivos) {
        List<Prize> lista = apenasAtivos
                ? premios.findByActiveTrueOrderByPriceAsc()
                : premios.findAllByOrderByPriceAsc();
        return lista.stream().map(PrizeResponse::de).toList();
    }

    @Transactional
    public PrizeResponse criarPremio(PrizeRequest pedido, String emailResponsavel) {
        Prize premio = new Prize(
                Texto.limitar(pedido.nome(), 100),
                Texto.limitar(pedido.descricao(), 300),
                pedido.preco(),
                pedido.ativo() == null || pedido.ativo(),
                emailResponsavel
        );
        return PrizeResponse.de(premios.save(premio));
    }

    @Transactional
    public PrizeResponse atualizarPremio(UUID id, PrizeRequest pedido, String emailResponsavel) {
        Prize premio = premios.findById(id)
                .orElseThrow(() -> new NotFoundException("Premio nao encontrado."));
        premio.atualizar(
                Texto.limitar(pedido.nome(), 100),
                Texto.limitar(pedido.descricao(), 300),
                pedido.preco(),
                pedido.ativo() == null || pedido.ativo(),
                emailResponsavel
        );
        return PrizeResponse.de(premios.save(premio));
    }

    @Transactional
    public void excluirPremio(UUID id) {
        Prize premio = premios.findById(id)
                .orElseThrow(() -> new NotFoundException("Premio nao encontrado."));
        // Resgates apontam para o premio, entao ele nunca e apagado de verdade:
        // desativar preserva o historico e some da lista de resgate do mesmo jeito.
        premio.atualizar(premio.getName(), premio.getDescription(), premio.getPrice(), false, "SISTEMA");
        premios.save(premio);
    }

    /**
     * Resgata um premio: debita o preco e registra o resgate na mesma transacao.
     *
     * <p>No Apps Script isso eram dois appendRow em abas diferentes, e uma falha entre
     * eles debitava as moedas sem entregar o premio.</p>
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public RedemptionResponse resgatar(UUID premioId, String emailUsuario) {
        Prize premio = premios.findById(premioId)
                .orElseThrow(() -> new NotFoundException("Premio nao encontrado."));

        if (!premio.isActive()) {
            throw new BusinessException("Este premio nao esta disponivel.");
        }

        int saldoAtual = lancamentos.saldo();
        if (saldoAtual < premio.getPrice()) {
            throw new BusinessException("Voce ainda nao possui Fofocoins suficientes.");
        }

        CoinEntry debito = lancamentos.save(new CoinEntry(
                -premio.getPrice(),
                "Resgate: " + premio.getName(),
                CoinEntry.SISTEMA
        ));

        Redemption resgate = resgates.save(new Redemption(premio, emailUsuario, debito.getId()));
        return RedemptionResponse.de(resgate, saldoAtual - premio.getPrice());
    }

    @Transactional(readOnly = true)
    public List<RedemptionResponse> listarResgates() {
        int saldoAtual = lancamentos.saldo();
        return resgates.findAllByOrderByCreatedAtDesc().stream()
                .map(resgate -> RedemptionResponse.de(resgate, saldoAtual))
                .toList();
    }
}
