package com.piggu.finance.api;

import com.piggu.finance.domain.AvisosDoDia;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Avisos de hoje da familia, como dado. Quem chama e o lembrete diario do identity,
 * com um token curto da propria pessoa; o app pode usar tambem.
 *
 * <p>Fora do contrato da API (docs/api). Interna: o lembrete diario do identity chama direto; o gateway nao roteia /api/alerts.</p>
 */
@Hidden
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AvisosDoDia avisos;

    public AlertController(AvisosDoDia avisos) {
        this.avisos = avisos;
    }

    @GetMapping
    public List<AvisosDoDia.Aviso> deHoje() {
        return avisos.deHoje();
    }
}
