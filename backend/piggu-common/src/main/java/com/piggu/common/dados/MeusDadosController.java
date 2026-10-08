package com.piggu.common.dados;

import com.fasterxml.jackson.databind.JsonNode;
import com.piggu.common.error.ForbiddenException;
import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exportar e apagar os dados da pessoa neste servico.
 *
 * <p>Fica fora da tabela de rotas do gateway: so o identity chama, para montar o
 * arquivo unico de exportacao e para a exclusao em cascata.</p>
 */
@RestController
@RequestMapping("/api/meus-dados")
public class MeusDadosController {

    private final DadosDaFamilia dados;

    public MeusDadosController(DadosDaFamilia dados) {
        this.dados = dados;
    }

    @GetMapping
    public Map<String, JsonNode> exportar(@AuthUser CurrentUser usuario) {
        return dados.exportar(usuario);
    }

    /** So com o token de exclusao que o identity emite; o token comum e recusado. */
    @DeleteMapping
    public ResponseEntity<Void> apagar(@AuthUser CurrentUser usuario, @AuthenticationPrincipal Jwt token) {
        EscopoDeExclusao escopo = EscopoDeExclusao.de(token.getClaimAsString(EscopoDeExclusao.CLAIM));
        if (escopo == null) {
            throw new ForbiddenException("Exclusao de dados so pelo pedido de exclusao da conta.");
        }
        dados.apagar(usuario, escopo);
        return ResponseEntity.noContent().build();
    }
}
