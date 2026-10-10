package com.piggu.finance.api;

import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.api.dto.ReceiptParseRequest;
import com.piggu.finance.api.dto.ReceiptParseResponse;
import com.piggu.finance.domain.CotaDeLeituras;
import com.piggu.finance.integration.LeitorDeRecibos;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leitura de recibo por foto. Substitui a acao parse.
 *
 * <p>Este endpoint nao grava nada: devolve os itens lidos para conferencia.</p>
 */
@RestController
@RequestMapping("/api/receipts")
@PreAuthorize("hasAnyRole('ADMIN', 'TITULAR', 'PARCEIRO')")
public class ReceiptController {

    private final LeitorDeRecibos leitor;
    private final CotaDeLeituras cota;

    public ReceiptController(LeitorDeRecibos leitor, CotaDeLeituras cota) {
        this.leitor = leitor;
        this.cota = cota;
    }

    @PostMapping("/parse")
    public ReceiptParseResponse ler(@Valid @RequestBody ReceiptParseRequest pedido,
                                    @AuthUser CurrentUser usuario) {
        return leitor.ler(pedido, usuario);
    }

    /** Quantas leituras a familia ja fez no mes e quantas sobram no gratuito. */
    @GetMapping("/usage")
    public CotaDeLeituras.Uso uso(@AuthUser CurrentUser usuario) {
        return cota.uso(usuario);
    }
}
