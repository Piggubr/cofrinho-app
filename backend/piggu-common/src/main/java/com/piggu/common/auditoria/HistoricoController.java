package com.piggu.common.auditoria;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Historico de mudancas da familia neste servico, em {@code /api/history} ou na rota de
 * {@code piggu.auditoria.rota} (o identity usa {@code /api/family/history}, que o gateway
 * ja manda para ele). Cada pessoa ve o que ela fez; titular e parceiro, tambem o que o
 * outro e os menores fizeram: os mesmos de quem ela ve os lancamentos
 * ({@link CurrentUser#visiveis()}).

 */
@RestController
@RequestMapping("${piggu.auditoria.rota:/api/history}")
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
        return trilha.recentes(usuario.familia(), entidade, Math.clamp(limite, 1, MAXIMO), usuario.visiveis(),
                usuario.isResponsavel());


    }
}
