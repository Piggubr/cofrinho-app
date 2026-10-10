package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.web.Meses;
import com.piggu.finance.api.dto.ExpenseResponse;
import com.piggu.finance.api.dto.SaveExpensesRequest;
import com.piggu.finance.api.dto.UpdateExpenseRequest;
import com.piggu.finance.domain.DivisaoDeGastos;
import com.piggu.finance.domain.ExpenseService;
import com.piggu.finance.domain.LeitorDeExtrato;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
 * <p>Substitui as acoes save, updateExpense e deleteExpense. O perfil MEMBRO nao
 * alcanca nada aqui, como era em autorizarAcao_.</p>
 */
@RestController
@RequestMapping("/api/expenses")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
public class ExpenseController {

    private final ExpenseService servico;
    private final DivisaoDeGastos divisao;

    public ExpenseController(ExpenseService servico, DivisaoDeGastos divisao) {
        this.servico = servico;
        this.divisao = divisao;
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
    @GetMapping("/search")
    public List<ExpenseResponse> buscar(@RequestParam @Size(max = 100) String q) {
        return servico.buscar(q);
    }

    /** Le o extrato (OFX ou CSV) e devolve a previa, sem gravar. */
    @PostMapping("/import/preview")
    public List<ExpenseService.LinhaDaPrevia> previa(@Valid @RequestBody ExtratoRequest pedido) {
        return servico.previaDoExtrato(pedido.conteudo());
    }

    /** Grava as linhas escolhidas; as ja importadas sao puladas pelo id externo. */
    @PostMapping("/import")
    public Map<String, Integer> importar(@Valid @RequestBody ImportRequest pedido, @AuthUser CurrentUser usuario) {
        int novas = servico.importar(pedido.linhas().stream()
                .map(l -> new ExpenseService.LinhaImportada(l.data(), l.descricao(), l.valor(), l.idExterno(), l.categoria()))
                .toList(), pedido.contaId(), usuario.email());
        return Map.of("importados", novas, "pulados", pedido.linhas().size() - novas);
    }

    /** Gastos em CSV; sem mes, todos. */
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
    @GetMapping("/splits")
    public List<DivisaoDeGastos.Acerto> acerto(@RequestParam(required = false) String mes) {
        return divisao.acerto(Meses.ouAtual(mes));
    }

    /**
     * @param mes filtro opcional no formato AAAA-MM; ausente devolve tudo
     */
    @GetMapping
    public List<ExpenseResponse> listar(@RequestParam(required = false) String mes) {
        return mes == null || mes.isBlank() ? servico.listar() : servico.listarDoMes(mes);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public List<ExpenseResponse> salvar(@Valid @RequestBody SaveExpensesRequest pedido,
                                        @AuthUser CurrentUser usuario) {
        return servico.salvar(pedido, usuario.email());
    }

    @PutMapping("/{id}")
    public ExpenseResponse atualizar(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateExpenseRequest pedido) {
        return servico.atualizar(id, pedido);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
