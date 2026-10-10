package com.piggu.common.auditoria;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Historico de mudancas da familia neste servico, em {@code /api/history} ou na rota de
 * {@code piggu.auditoria.rota} (o identity usa {@code /api/family/history}, que o gateway
 * ja manda para ele). So quem lanca (titular, parceiro e
 * ADMIN) ve: o membro nao enxerga os lancamentos, entao tambem nao ve quem os mudou.
 */
@RestController
@RequestMapping("${piggu.auditoria.rota:/api/history}")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
public class HistoricoController {

    private static final int MAXIMO = 200;

    private final TrilhaDeAuditoria trilha;

    public HistoricoController(TrilhaDeAuditoria trilha) {
        this.trilha = trilha;
    }

    @GetMapping
    public List<TrilhaDeAuditoria.Evento> recentes(@AuthUser CurrentUser usuario,
                                                  @RequestParam(required = false) String entidade,
                                                  @RequestParam(defaultValue = "100") int limite) {
        return trilha.recentes(usuario.familia(), entidade, Math.clamp(limite, 1, MAXIMO));
    }
}
