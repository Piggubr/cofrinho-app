package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.domain.ObjetivosService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cofrinhos por objetivo. Gratuito tem um; Premium, varios.
 *
 * <p>Fica fora do alcance do membro: o total de cada objetivo soma o deposito de
 * todos, e o membro so ve o que ele mesmo depositou.</p>
 */
@RestController
@RequestMapping("/api/piggy-bank/goals")
public class SavingsGoalController {

    private final ObjetivosService servico;

    public SavingsGoalController(ObjetivosService servico) {
        this.servico = servico;
    }

    /** @param prazo ultimo dia para juntar; ausente e sem prazo */
    public record ObjetivoRequest(
            @NotBlank(message = "De um nome ao objetivo.") @Size(max = 60, message = "O nome e longo demais.")
            String nome,
            @NotNull(message = "Digite quanto quer juntar.")
            @DecimalMin(value = "0.01", message = "Digite quanto quer juntar.")
            @DecimalMax(value = "10000000", message = "O valor e alto demais.") BigDecimal alvo,
            LocalDate prazo) {
    }

    @GetMapping
    public List<ObjetivosService.Situacao> listar() {
        return servico.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ObjetivosService.Situacao criar(@Valid @RequestBody ObjetivoRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.criar(pedido.nome(), pedido.alvo(), pedido.prazo(), usuario);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> alterar(@PathVariable UUID id, @Valid @RequestBody ObjetivoRequest pedido) {
        servico.alterar(id, pedido.nome(), pedido.alvo(), pedido.prazo());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        servico.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
