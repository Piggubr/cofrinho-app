package com.piggu.finance.api;

import com.piggu.finance.api.dto.ReceiptParseRequest;
import com.piggu.finance.api.dto.ReceiptParseResponse;
import com.piggu.finance.integration.GeminiReceiptReader;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("hasAnyRole('ADMIN', 'BEATRIZ')")
public class ReceiptController {

    private final GeminiReceiptReader leitor;

    public ReceiptController(GeminiReceiptReader leitor) {
        this.leitor = leitor;
    }

    @PostMapping("/parse")
    public ReceiptParseResponse ler(@Valid @RequestBody ReceiptParseRequest pedido) {
        return leitor.ler(pedido);
    }
}
