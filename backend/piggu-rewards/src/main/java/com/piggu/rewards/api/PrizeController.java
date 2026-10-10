package com.piggu.rewards.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.rewards.api.dto.PrizeRequest;
import com.piggu.rewards.api.dto.PrizeResponse;
import com.piggu.rewards.api.dto.RedemptionResponse;
import com.piggu.rewards.domain.RewardsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

import java.util.List;
import java.util.UUID;

/** Premios e resgates. Substitui as acoes savePrize e redeemPrize. */
@RestController
@RequestMapping("/api/prizes")
public class PrizeController {

    private final RewardsService servico;

    public PrizeController(RewardsService servico) {
        this.servico = servico;
    }

    /**
     * @param todos quando verdadeiro inclui os premios desativados; so o administrador
     *              precisa disso, na tela de gestao
     */
    @GetMapping
    public List<PrizeResponse> listar(@RequestParam(defaultValue = "false") boolean todos) {
        return servico.listarPremios(!todos);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
    public PrizeResponse criar(@Valid @RequestBody PrizeRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.criarPremio(pedido, usuario.email());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
    public PrizeResponse atualizar(@PathVariable UUID id,
                                   @Valid @RequestBody PrizeRequest pedido,
                                   @AuthUser CurrentUser usuario) {
        return servico.atualizarPremio(id, pedido, usuario.email());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        servico.excluirPremio(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/redemptions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
    public RedemptionResponse resgatar(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        return servico.resgatar(id, usuario.email());
    }

    @GetMapping("/redemptions")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
    public List<RedemptionResponse> historicoDeResgates() {
        return servico.listarResgates();
    }
}
