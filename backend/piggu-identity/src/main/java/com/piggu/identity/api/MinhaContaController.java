package com.piggu.identity.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.api.dto.UserResponse;
import com.piggu.identity.domain.MinhaContaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** CPF e foto da conta, copia dos dados e exclusao da conta (LGPD art. 18). Gratis em qualquer plano e papel. */
@RestController
@RequestMapping("/api/me")
public class MinhaContaController {

    private final MinhaContaService servico;

    public MinhaContaController(MinhaContaService servico) {
        this.servico = servico;
    }

    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> exportar(@AuthUser CurrentUser usuario, @AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("piggu-meus-dados.json").build().toString())
                .body(servico.exportar(usuario, token.getTokenValue()));
    }

    public record CpfRequest(@NotBlank(message = "Informe o CPF.") @Size(max = 20) String cpf) {
    }

    /** Uma vez por conta; depois de informado o CPF fica preso a ela. */
    @PutMapping("/cpf")
    public UserResponse informarCpf(@Valid @RequestBody CpfRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.informarCpf(usuario.id(), pedido.cpf());
    }

    public record ResidenciaRequest(@NotNull(message = "Diga se mora no Brasil.") Boolean moraNoBrasil) {
    }

    /** No Brasil o CPF e obrigatorio; quem mora fora segue sem ele. */
    @PutMapping("/residencia")
    public UserResponse declararResidencia(@Valid @RequestBody ResidenciaRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.declararResidencia(usuario.id(), pedido.moraNoBrasil());
    }

    public record FotoRequest(@NotBlank(message = "Escolha uma foto.") @Size(max = 250_000) String imagemBase64,
                              @NotBlank @Size(max = 40) String mimeType) {
    }

    public record TermosRequest(@NotBlank(message = "Informe a versao dos termos.") @Size(max = 20) String versao) {
    }

    /** Os Termos mudaram: a pessoa aceita a versao nova para seguir usando o app. */
    @PutMapping("/termos")
    public UserResponse aceitarTermos(@Valid @RequestBody TermosRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.aceitarTermos(usuario.id(), pedido.versao());
    }

    /** Foto de perfil propria; o servidor reduz, recorta e regrava antes de guardar. */
    @PutMapping("/foto")
    public UserResponse trocarFoto(@Valid @RequestBody FotoRequest pedido, @AuthUser CurrentUser usuario) {
        return servico.trocarFoto(usuario.id(), pedido.imagemBase64(), pedido.mimeType());
    }

    /** Sem foto nenhuma: ficam as iniciais. */
    @DeleteMapping("/foto")
    public UserResponse removerFoto(@AuthUser CurrentUser usuario) {
        return servico.removerFoto(usuario.id());
    }

    /** Volta a foto da conta Google. */
    @PutMapping("/foto/google")
    public UserResponse usarFotoDoGoogle(@AuthUser CurrentUser usuario) {
        return servico.usarFotoDoGoogle(usuario.id());
    }

    @DeleteMapping
    public ResponseEntity<Void> excluir(@AuthUser CurrentUser usuario) {
        servico.excluir(usuario.id());
        return ResponseEntity.noContent().build();
    }
}
