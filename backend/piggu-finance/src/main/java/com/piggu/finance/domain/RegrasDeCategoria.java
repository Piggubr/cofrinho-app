package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Categoria automatica: regras da familia primeiro, depois a categoria que o produto
 * ja teve na memoria de precos.
 */
@Service
public class RegrasDeCategoria {

    private final CategoryRuleRepository regras;
    private final ProductMemoryRepository memoria;
    private final CategoryService categorias;

    public RegrasDeCategoria(CategoryRuleRepository regras, ProductMemoryRepository memoria,
                             CategoryService categorias) {
        this.regras = regras;
        this.memoria = memoria;
        this.categorias = categorias;
    }

    public record Regra(UUID id, String termo, String categoria) {
    }

    @Transactional(readOnly = true)
    public List<Regra> listar() {
        return regras.findAllByOrderByTermAsc().stream()
                .map(r -> new Regra(r.getId(), r.getTerm(), r.getCategory()))
                .toList();
    }

    /** Cria a regra, ou troca a categoria se o termo ja tem uma. */
    @Transactional
    public Regra definir(String termoBruto, String categoria, UUID pessoa) {
        String termo = ChaveProduto.de(termoBruto);
        if (termo.length() < 2 || termo.length() > 100) {
            throw new BusinessException("Digite um termo valido para a regra.");
        }
        String valida = categorias.normalizar(categoria);
        CategoryRule regra = regras.findByTerm(termo)
                .map(existente -> {
                    existente.alterarCategoria(valida, pessoa);
                    return existente;
                })
                .orElseGet(() -> regras.save(new CategoryRule(termo, valida, pessoa)));
        return new Regra(regra.getId(), regra.getTerm(), regra.getCategory());
    }

    @Transactional
    public void excluir(UUID id) {
        regras.delete(regras.findById(id).orElseThrow(() -> new NotFoundException("Regra nao encontrada.")));
    }

    /** As regras da familia, carregadas uma vez por lancamento. */
    @Transactional(readOnly = true)
    public List<CategoryRule> carregar() {
        return regras.findAllByOrderByTermAsc();
    }

    /** Regra que casa com o item ou o estabelecimento; o termo mais longo vence ("pao de queijo" ganha de "pao"). */
    static Optional<String> porRegra(List<CategoryRule> regras, String item, String estabelecimento) {
        String texto = " " + ChaveProduto.de(item) + " " + ChaveProduto.de(estabelecimento) + " ";
        return regras.stream()
                .filter(r -> texto.contains(" " + r.getTerm() + " "))
                .max(Comparator.comparingInt(r -> r.getTerm().length()))
                .map(CategoryRule::getCategory);
    }

    /** Categoria que o mesmo produto teve da ultima vez. */
    @Transactional(readOnly = true)
    public Optional<String> daMemoria(String item) {
        String chave = ChaveProduto.de(item);
        return chave.isEmpty() ? Optional.empty() : memoria.findByProductKey(chave).map(ProductMemory::getCategory);
    }
}
