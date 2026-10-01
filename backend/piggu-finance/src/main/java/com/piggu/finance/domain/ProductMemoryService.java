package com.piggu.finance.domain;

import com.piggu.finance.api.dto.ProductResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Memoria de precos do mercado.
 *
 * <p>Porte de atualizarMemoriaProdutos_. O original lia a aba inteira, montava um
 * indice em memoria e reescrevia todas as linhas a cada compra, protegido por
 * LockService. Aqui cada produto e uma linha propria: o banco resolve a concorrencia
 * e so o que mudou e gravado.</p>
 */
@Service
public class ProductMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ProductMemoryService.class);

    private final ProductMemoryRepository repositorio;

    public ProductMemoryService(ProductMemoryRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listar() {
        return repositorio.findAllByOrderByPurchasesDesc().stream().map(ProductResponse::de).toList();
    }

    /** Nomes e categorias mais frequentes, usados para orientar a leitura de recibos. */
    @Transactional(readOnly = true)
    public List<String> memoriaParaIa(int limite) {
        return repositorio.findAllByOrderByPurchasesDesc().stream()
                .limit(limite)
                .map(produto -> produto.getName() + " = " + produto.getCategory())
                .toList();
    }

    /**
     * Registra os itens de um lancamento na memoria de precos.
     *
     * <p>Falhar aqui nao pode derrubar o lancamento: o gasto ja foi aceito e a memoria
     * de precos e um apoio, nao um dado critico.</p>
     */
    @Transactional
    public void registrar(List<Expense> gastos) {
        for (Expense gasto : gastos) {
            try {
                registrarUm(gasto);
            } catch (RuntimeException erro) {
                log.warn("Nao foi possivel atualizar a memoria de precos do gasto {}", gasto.getId(), erro);
            }
        }
    }

    private void registrarUm(Expense gasto) {
        String chave = ChaveProduto.de(gasto.getItem());
        if (chave.isEmpty()) {
            return;
        }
        LocalDate data = gasto.getExpenseDate();
        repositorio.findById(chave).ifPresentOrElse(
                produto -> {
                    produto.registrarCompra(gasto.getItem(), gasto.getCategory(),
                            gasto.getAmount(), data, gasto.getUserEmail());
                    repositorio.save(produto);
                },
                () -> repositorio.save(new ProductMemory(chave, gasto.getItem(), gasto.getCategory(),
                        gasto.getAmount(), data, gasto.getUserEmail()))
        );
    }
}
