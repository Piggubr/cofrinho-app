package com.piggu.identity.api;

import com.piggu.common.error.UnauthorizedException;
import com.piggu.common.web.Texto;
import com.piggu.identity.google.LoginPeloNavegador;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * As duas pontas do login com Google no app instalado (ver {@link LoginPeloNavegador}).
 *
 * <p>A volta do Google chega no navegador do celular, fora do app: em vez de JSON, a resposta e
 * uma pagina curta que reabre o Piggu pelo link piggu://, com um botao caso o navegador nao abra
 * sozinho.</p>
 */
@Tag(name = "Login e sessão")
@RestController
@RequestMapping("/api/auth/google/app")
public class LoginNoAppController {

    private static final Logger log = LoggerFactory.getLogger(LoginNoAppController.class);
    private static final String VOLTA = "piggu://entrar";

    private final LoginPeloNavegador navegador;

    public LoginNoAppController(LoginPeloNavegador navegador) {
        this.navegador = navegador;
    }

    /** O Google devolve aqui (response_mode=form_post) o ID token, o state ou o erro. */
    @Operation(summary = "Volta do Google no navegador do celular (login no app)")
    @SecurityRequirements
    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> voltarDoGoogle(@RequestParam(name = "id_token", required = false) String idToken,
                                                 @RequestParam(name = "state", required = false) String state,
                                                 @RequestParam(name = "error", required = false) String erro) {
        if (!Texto.vazio(erro) || Texto.vazio(idToken)) {
            // access_denied: a pessoa cancelou no Google. Volta ao app sem nada.
            return pagina(VOLTA, "Login cancelado", "Volte para o Piggu para entrar de outro jeito.");
        }
        try {
            String codigo = navegador.guardar(idToken, state);
            return pagina(VOLTA + "?google=" + codigo, "Pronto!", "Agora é só voltar para o Piggu.");
        } catch (UnauthorizedException recusa) {
            log.info("Volta do Google recusada no login do app: {}", recusa.getMessage());
            return pagina(VOLTA, "Não deu certo", "Volte para o Piggu e tente entrar com o Google de novo.");
        }
    }

    public record TrocaRequest(@NotBlank @Size(max = 100) String codigo,
                               @NotBlank @Size(max = 200) String verificador) {
    }

    public record TrocaResponse(String idToken) {
    }

    /** O app, de volta, troca o codigo e o verificador pelo ID token que segue para /api/auth/google. */
    @Operation(summary = "Trocar o código do login no app pelo ID token do Google")
    @SecurityRequirements
    @PostMapping("/troca")
    public TrocaResponse trocar(@Valid @RequestBody TrocaRequest pedido) {
        return new TrocaResponse(navegador.trocar(pedido.codigo(), pedido.verificador()));
    }

    /** O destino e sempre piggu://entrar com um codigo base64url: nada vindo de fora entra no HTML. */
    private static ResponseEntity<String> pagina(String destino, String titulo, String texto) {
        String html = """
                <!doctype html>
                <html lang="pt-BR">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta http-equiv="refresh" content="0;url=%1$s">
                <title>Piggu</title>
                <style>
                  body { margin: 0; min-height: 100vh; display: grid; place-items: center; text-align: center;
                         font-family: -apple-system, system-ui, sans-serif; background: #fff7f9; color: #3a2530; }
                  main { padding: 24px; max-width: 320px; }
                  h1 { font-size: 1.4rem; margin: 0 0 8px; }
                  p { margin: 0 0 24px; line-height: 1.4; }
                  a { display: inline-block; padding: 14px 28px; border-radius: 999px; background: #d6457a;
                      color: #fff; text-decoration: none; font-weight: 600; }
                  @media (prefers-color-scheme: dark) { body { background: #1d1418; color: #f6e9ee; } }
                </style>
                </head>
                <body>
                <main>
                  <h1>%2$s</h1>
                  <p>%3$s</p>
                  <a href="%1$s">Abrir o Piggu</a>
                </main>
                </body>
                </html>
                """.formatted(destino, titulo, texto);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }
}
