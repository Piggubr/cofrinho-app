package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.error.NotFoundException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Texto;
import com.piggu.finance.api.dto.ExpenseItemRequest;
import com.piggu.finance.api.dto.ExpenseResponse;
import com.piggu.finance.api.dto.NoteRequest;
import com.piggu.finance.api.dto.NoteResponse;
import com.piggu.finance.api.dto.SaveExpensesRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Notas e lembretes.
 *
 * <p>Porte de salvarNota_ e excluirNota_. A regra que da trabalho e o vinculo com
 * gastos: uma nota com valor cria um gasto, e apagar a nota apaga esse gasto.
 * No Apps Script eram duas escritas em abas diferentes sem transacao, e uma falha
 * no meio deixava gasto sem nota. Aqui as duas entidades vivem no mesmo servico e
 * no mesmo banco, entao uma unica transacao cobre as duas.</p>
 */
@Service
public class NoteService {

    private static final String ORIGEM_NOTA = "Nota";

    private final NoteRepository repositorio;
    private final ExpenseService gastos;
    private final CategoryService categorias;

    public NoteService(NoteRepository repositorio, ExpenseService gastos, CategoryService categorias) {
        this.repositorio = repositorio;
        this.gastos = gastos;
        this.categorias = categorias;
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> listar() {
        return repositorio.findAllByOrderByCreatedAtDesc().stream().map(NoteResponse::de).toList();
    }

    @Transactional
    public NoteResponse criar(NoteRequest pedido, UUID usuarioId) {
        BigDecimal valor = pedido.valor() == null ? BigDecimal.ZERO : pedido.valor();
        boolean eventoPago = valor.compareTo(BigDecimal.ZERO) > 0;

        if (eventoPago && pedido.data() == null) {
            throw new BusinessException("Escolha a data do evento pago.");
        }

        String titulo = Texto.limitar(pedido.titulo(), 150);
        String categoria = categorias.normalizar(pedido.categoria());

        UUID gastoId = null;
        if (eventoPago) {
            List<ExpenseResponse> criados = gastos.salvar(new SaveExpensesRequest(
                    pedido.data(),
                    "",
                    null,
                    ORIGEM_NOTA,
                    List.of(new ExpenseItemRequest(titulo, categoria, valor, "Variavel"))
            ), usuarioId);
            gastoId = criados.get(0).id();
        }

        Note nota = new Note(
                titulo,
                Texto.limitar(pedido.texto(), 1500),
                pedido.data(),
                valor,
                eventoPago ? categoria : "",
                gastoId,
                usuarioId
        );
        return NoteResponse.de(repositorio.save(nota));
    }

    @Transactional
    public void excluir(UUID id, CurrentUser usuario) {
        Note nota = repositorio.findById(id)
                .orElseThrow(() -> new NotFoundException("Nota nao encontrada."));

        if (!usuario.podeGerenciar(nota.getUserId())) {
            throw new ForbiddenException("Voce nao pode apagar esta nota.");
        }

        gastos.excluirSeExistir(nota.getExpenseId());
        repositorio.delete(nota);
    }
}
