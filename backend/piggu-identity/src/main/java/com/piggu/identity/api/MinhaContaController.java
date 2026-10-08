package com.piggu.identity.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.identity.domain.MinhaContaService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Copia dos dados e exclusao da conta (LGPD art. 18). Gratis em qualquer plano e papel. */
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

    @DeleteMapping
    public ResponseEntity<Void> excluir(@AuthUser CurrentUser usuario) {
        servico.excluir(usuario.id());
        return ResponseEntity.noContent().build();
    }
}
