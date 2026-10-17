package com.piggu.identity.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.identity.api.dto.FamiliaResponse;
import com.piggu.identity.domain.FamiliaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** A familia de quem esta logado: ver, renomear, convidar, remover e sair. */
@RestController
@RequestMapping("/api/family")
public class FamiliaController {

    private final FamiliaService servico;

    public FamiliaController(FamiliaService servico) {
        this.servico = servico;
    }

    public record NomeRequest(@NotBlank(message = "Digite o nome da familia.")
                              @Size(max = 120, message = "O nome e longo demais.") String nome) {
    }

    public record ConviteRequest(@NotBlank(message = "Digite um e-mail valido.")
                                 @Email(message = "Digite um e-mail valido.")
                                 @Size(max = 320, message = "O e-mail e longo demais.") String email) {
    }

    @GetMapping
    public FamiliaResponse ver(@AuthUser CurrentUser usuario) {
        return servico.ver(usuario);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public FamiliaResponse renomear(@Valid @RequestBody NomeRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.renomear(usuario, pedido.nome());
    }

    @PostMapping("/invites")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public FamiliaResponse convidar(@Valid @RequestBody ConviteRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.convidar(usuario, pedido.email());
    }

    @DeleteMapping("/invites/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public FamiliaResponse cancelarConvite(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        return servico.cancelarConvite(usuario, id);
    }

    @DeleteMapping("/members/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public FamiliaResponse removerMembro(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        return servico.removerMembro(usuario, id);
    }

    public record PapelRequest(@NotNull(message = "Escolha o papel.") PigguRole papel) {
    }

    /** Promove a parceiro ou volta a membro. */
    @PutMapping("/members/{id}/role")
    @PreAuthorize("hasAnyRole('ADMIN', 'TITULAR')")
    public FamiliaResponse mudarPapel(@PathVariable UUID id, @Valid @RequestBody PapelRequest pedido,
                                      @AuthUser CurrentUser usuario) {
        return servico.mudarPapel(usuario, id, pedido.papel());
    }

    /** Convites de outras familias para o e-mail de quem esta logado. */
    @GetMapping("/invites/mine")
    public List<FamiliaResponse.Convite> convitesParaMim(@AuthUser CurrentUser usuario) {
        return servico.convitesParaMim(usuario);
    }

    /** Entra na familia que convidou; os dados da familia atual saem. Precisa entrar de novo. */
    @PostMapping("/invites/{id}/accept")
    public ResponseEntity<Void> aceitar(@PathVariable UUID id, @AuthUser CurrentUser usuario) {
        servico.aceitarConvite(usuario, id);
        return ResponseEntity.noContent().build();
    }

    /** O membro sai e passa a ter uma familia propria; precisa entrar de novo. */
    @PostMapping("/leave")
    public ResponseEntity<Void> sair(@AuthUser CurrentUser usuario) {
        servico.sair(usuario);
        return ResponseEntity.noContent().build();
    }
}
