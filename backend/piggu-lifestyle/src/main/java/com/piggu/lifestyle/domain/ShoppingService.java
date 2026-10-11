package com.piggu.lifestyle.domain;

import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Texto;
import com.piggu.lifestyle.api.dto.ShoppingItemRequest;
import com.piggu.lifestyle.api.dto.ShoppingItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Listas de compras e de desejos.
 *
 * <p>Porte de salvarItemCompra_, alternarItemCompra_, excluirItemCompra_ e
 * carregarCompras_.</p>
 */
@Service
public class ShoppingService {

    private static final Pattern SOMENTE_DIGITOS = Pattern.compile("\\D");
    /** So fotos do catalogo (Open Food Facts), o mesmo host que a CSP do site libera. */
    private static final Pattern URL_SEGURA =
            Pattern.compile("^https://(images|static)[.]openfoodfacts[.]org/[^\\s\"'<>]+$");

    private final ShoppingItemRepository repositorio;

    public ShoppingService(ShoppingItemRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public List<ShoppingItemResponse> listar(String lista) {
        List<ShoppingItem> itens = Texto.vazio(lista)
                ? repositorio.findAllByOrderByCreatedAtDesc()
                : repositorio.findByListNameOrderByCreatedAtDesc(normalizarLista(lista));
        return itens.stream().map(ShoppingItemResponse::de).toList();
    }

    @Transactional
    public ShoppingItemResponse criar(ShoppingItemRequest pedido, UUID usuarioId) {
        ShoppingItem item = new ShoppingItem(
                Texto.limitar(pedido.item(), 150),
                Texto.limitar(pedido.quantidade(), 50),
                normalizarLista(pedido.lista()),
                Texto.limitar(pedido.marca(), 100),
                imagemSegura(pedido.imagem()),
                somenteDigitos(pedido.codigo()),
                usuarioId
        );
        return ShoppingItemResponse.de(repositorio.save(item));
    }

    @Transactional
    public ShoppingItemResponse alternarComprado(UUID id, boolean comprado) {
        ShoppingItem item = buscar(id);
        item.marcarComprado(comprado);
        return ShoppingItemResponse.de(repositorio.save(item));
    }

    @Transactional
    public void excluir(UUID id, CurrentUser usuario) {
        ShoppingItem item = buscar(id);
        if (!usuario.podeGerenciar(item.getUserId())) {
            throw new ForbiddenException("Voce nao pode apagar este item.");
        }
        repositorio.delete(item);
    }

    private ShoppingItem buscar(UUID id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new NotFoundException("Item nao encontrado."));
    }

    private String normalizarLista(String lista) {
        return ShoppingItem.LISTA_DESEJOS.equalsIgnoreCase(Texto.limitar(lista, 20))
                ? ShoppingItem.LISTA_DESEJOS
                : ShoppingItem.LISTA_COMPRAS;
    }

    /**
     * So aceita imagem do catalogo.
     *
     * <p>A URL chega do navegador e acaba numa tag img que a familia inteira abre: um
     * host qualquer receberia o IP de todos. Ficam so as fotos do Open Food Facts, que
     * o aviso de privacidade lista e a CSP libera.</p>
     */
    private String imagemSegura(String url) {
        String limpo = Texto.limitar(url, 1000);
        return URL_SEGURA.matcher(limpo).matches() ? limpo : "";
    }

    private String somenteDigitos(String codigo) {
        return Texto.limitar(SOMENTE_DIGITOS.matcher(Texto.limitar(codigo, 40)).replaceAll(""), 20);
    }
}
