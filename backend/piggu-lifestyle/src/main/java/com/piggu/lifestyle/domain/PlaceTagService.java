package com.piggu.lifestyle.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.web.Texto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Marcadores de lugar: os quatro de fabrica mais os criados pelo usuario.
 * Porte da parte MARCADOR_LUGAR de adicionarConfiguracao_.
 */
@Service
public class PlaceTagService {

    private final CustomPlaceTagRepository repositorio;

    public PlaceTagService(CustomPlaceTagRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public List<String> listar() {
        List<String> todos = new ArrayList<>(MarcadoresLugar.BASE);
        repositorio.findAllByOrderByNameAsc().stream()
                .map(CustomPlaceTag::getName)
                .filter(nome -> todos.stream().noneMatch(nome::equalsIgnoreCase))
                .forEach(todos::add);
        return todos;
    }

    /** Descarta marcadores desconhecidos em vez de recusar o lugar inteiro. */
    @Transactional(readOnly = true)
    public List<String> filtrarValidos(List<String> informados) {
        if (informados == null || informados.isEmpty()) {
            return List.of();
        }
        List<String> validos = listar();
        return informados.stream()
                .filter(marcador -> validos.stream().anyMatch(marcador::equalsIgnoreCase))
                .distinct()
                .toList();
    }

    @Transactional
    public String criar(String nomeBruto, UUID usuarioId) {
        String nome = Texto.limitar(Texto.espacoUnico(nomeBruto), 50);
        if (nome.length() < 2) {
            throw new BusinessException("Digite um nome valido.");
        }
        if (listar().stream().anyMatch(nome::equalsIgnoreCase)) {
            throw new BusinessException("Essa opcao ja existe.");
        }
        repositorio.save(new CustomPlaceTag(nome, usuarioId));
        return nome;
    }
}
