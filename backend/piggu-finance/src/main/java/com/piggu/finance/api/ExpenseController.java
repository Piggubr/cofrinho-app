package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.finance.api.dto.ExpenseResponse;
import com.piggu.finance.api.dto.SaveExpensesRequest;
import com.piggu.finance.api.dto.UpdateExpenseRequest;
import com.piggu.finance.domain.ContasFixasService;
import com.piggu.finance.domain.DivisaoDeGastos;
import com.piggu.finance.domain.ExpenseService;
import com.piggu.finance.domain.LeitorDeExtrato;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gastos.
 *
 * <p>Substitui as acoes save, updateExpense e deleteExpense. Todo perfil da familia
 * alcanca; cada um ve e altera o que e dele e o que os outros compartilharam (ver
 * CadaUmVeOSeuTest e CompartilhadoTest). Listas e somas aceitam {@code visao=meu|casal}.</p>
 */
@Tag(name = "Gastos", description = "Gastos: lançar, listar, buscar, importar, exportar e dividir.")
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService servico;
    private final DivisaoDeGastos divisao;
    private final ContasFixasService contasFixas;

    public ExpenseController(ExpenseService servico, DivisaoDeGastos divisao, ContasFixasService contasFixas) {
        this.servico = servico;
        this.divisao = divisao;
        this.contasFixas = contasFixas;
    }

    public record ExtratoRequest(
            @NotBlank(message = "O arquivo esta vazio.")
            @Size(max = 2_000_000, message = "Arquivo grande demais (maximo 2 MB).") String conteudo) {
    }

    public record LinhaRequest(
            @NotNull LocalDate data,
            @NotBlank @Size(max = 200) String descricao,
            @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal valor,
            @NotBlank @Size(max = 120) String idExterno,
            @Size(max = 50) String categoria) {
    }

    public record ImportRequest(
            UUID contaId,
            @NotEmpty(message = "Escolha ao menos um lancamento.")
            @Size(max = LeitorDeExtrato.MAXIMO_DE_LINHAS) List<@Valid LinhaRequest> linhas) {
    }

    /** Busca global (Ctrl+K): gastos pelo item ou estabelecimento. */
    @Operation(summary = "Buscar gastos pelo item ou estabelecimento")
    @GetMapping("/search")
    public List<ExpenseResponse> buscar(@RequestParam @Size(max = 100) String q) {
        return servico.buscar(q);
    }

    /** Le o extrato (OFX ou CSV) e devolve a previa, sem gravar. */
    @Operation(summary = "Prévia da importação de extrato (OFX ou CSV)")
    @PostMapping("/import/preview")
    public List<ExpenseService.LinhaDaPrevia> previa(@Valid @RequestBody ExtratoRequest pedido) {
        return servico.previaDoExtrato(pedido.conteudo());
    }

    public record LinhasRequest(
            @NotEmpty(message = "Nenhum lancamento para revisar.")
            @Size(max = LeitorDeExtrato.MAXIMO_DE_LINHAS) List<@Valid LinhaRequest> linhas) {
    }

    /** Previa de linhas ja separadas (gastos lidos do banco conectado), sem gravar. */
    @Operation(summary = "Prévia da importação de linhas já separadas")
    @PostMapping("/import/preview-lines")
    public List<ExpenseService.LinhaDaPrevia> previaDeLinhas(@Valid @RequestBody LinhasRequest pedido) {
        return servico.previa(pedido.linhas().stream()
                .map(l -> new LeitorDeExtrato.Linha(l.data(), l.descricao(), l.valor(), l.idExterno()))
                .toList());
    }

    /** Grava as linhas escolhidas; as ja importadas sao puladas pelo id externo. */
    @Operation(summary = "Importar os gastos escolhidos")
    @PostMapping("/import")
    public Map<String, Integer> importar(@Valid @RequestBody ImportRequest pedido, @AuthUser CurrentUser usuario) {
        int novas = servico.importar(pedido.linhas().stream()
                .map(l -> new ExpenseService.LinhaImportada(l.data(), l.descricao(), l.valor(), l.idExterno(), l.categoria()))
                .toList(), pedido.contaId(), usuario.id());
        return Map.of("importados", novas, "pulados", pedido.linhas().size() - novas);
    }

    /** Gastos em CSV; sem mes, todos. */
    @Operation(summary = "Exportar gastos em CSV")
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportar(@RequestParam(required = false) String mes) {
        String nome = "piggu-gastos" + (mes == null || mes.isBlank() ? "" : "-" + Meses.ouAtual(mes)) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                // BOM para o Excel abrir os acentos certos.
                .body("\uFEFF" + servico.exportarCsv(mes == null || mes.isBlank() ? null : Meses.ouAtual(mes).toString()));
    }

    /** Acerto dos gastos divididos no mes: quanto cada pessoa pagou, a parte dela e o saldo. */
    @Operation(summary = "Acerto dos gastos divididos no mês")
    @GetMapping("/splits")
    public List<DivisaoDeGastos.Acerto> acerto(@RequestParam(required = false) String mes) {
        return divisao.acerto(Meses.ouAtual(mes));
    }

    /** Quem deve quanto a quem nos gastos divididos do mes, com o menor numero de transferencias. */
    @Operation(summary = "Quem deve a quem nos gastos divididos")
    @GetMapping("/splits/transfers")
    public List<DivisaoDeGastos.Transferencia> quemDeveAQuem(@RequestParam(required = false) String mes) {
        return divisao.quemDeveAQuem(Meses.ouAtual(mes));
    }

    /**
     * @param mes filtro opcional no formato AAAA-MM; ausente devolve tudo
     */
    @Operation(summary = "Listar gastos")
    @GetMapping
    public List<ExpenseResponse> listar(@RequestParam(required = false) String mes) {
        return mes == null || mes.isBlank() ? servico.listar() : servico.listarDoMes(mes);
    }

    @Operation(summary = "Lançar gasto")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<ExpenseResponse> salvar(@Valid @RequestBody SaveExpensesRequest pedido,
                                        @AuthUser CurrentUser usuario) {
        return Boolean.TRUE.equals(pedido.recorrente()) ? contasFixas.lancarGastoRecorrente(pedido, usuario.id())
                : servico.salvar(pedido, usuario.id());
    }

    @Operation(summary = "Alterar gasto")
    @PutMapping("/{id}")
    public ExpenseResponse atualizar(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateExpenseRequest pedido,
                                     @AuthUser CurrentUser usuario) {
        return servico.atualizar(id, pedido, usuario);
    }

    @Operation(summary = "Excluir gasto")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id, @AuthUser CurrentUser usuario,
                                        @RequestParam(defaultValue = "false") boolean proximas) {
        servico.excluir(id, usuario, proximas);

        return ResponseEntity.noContent().build();
    }
}
