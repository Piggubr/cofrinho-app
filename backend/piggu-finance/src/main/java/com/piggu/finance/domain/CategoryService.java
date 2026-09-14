package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.web.Texto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Categorias validas do sistema: as oito de fabrica mais as criadas pelo usuario.
 *
 * <p>Porte de listarCategorias_ e da parte CATEGORIA de adicionarConfiguracao_.</p>
 */
@Service
public class CategoryService {

    private final CustomCategoryRepository repositorio;

    public CategoryService(CustomCategoryRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public List<String> listar() {
        List<String> todas = new ArrayList<>(Categorias.BASE);
        repositorio.findAllByOrderByNameAsc().stream()
                .map(CustomCategory::getName)
                .filter(nome -> todas.stream().noneMatch(nome::equalsIgnoreCase))
                .forEach(todas::add);
        return todas;
    }

    /**
     * Garante que a categoria informada existe.
     *
     * <p>Mesma decisao do Apps Script: categoria desconhecida nao derruba o lancamento,
     * cai em Outros. Perder o gasto seria pior do que classifica-lo mal.</p>
     */
    @Transactional(readOnly = true)
    public String normalizar(String categoria) {
        if (Texto.vazio(categoria)) {
            return Categorias.PADRAO;
        }
        return listar().stream()
                .filter(valida -> valida.equalsIgnoreCase(categoria.trim()))
                .findFirst()
                .orElse(Categorias.PADRAO);
    }

    @Transactional
    public String criar(String nomeBruto, String emailUsuario) {
        String nome = Texto.limitar(Texto.espacoUnico(nomeBruto), 50);
        if (nome.length() < 2) {
            throw new BusinessException("Digite um nome valido.");
        }
        boolean jaExiste = listar().stream()
                .anyMatch(existente -> existente.toLowerCase(Locale.ROOT).equals(nome.toLowerCase(Locale.ROOT)));
        if (jaExiste) {
            throw new BusinessException("Essa opcao ja existe.");
        }
        repositorio.save(new CustomCategory(nome, emailUsuario));
        return nome;
    }
}
