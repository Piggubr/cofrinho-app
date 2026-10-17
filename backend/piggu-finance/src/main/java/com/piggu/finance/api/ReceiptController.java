package com.piggu.finance.api;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.security.AuthUser;
import com.piggu.common.security.CurrentUser;
import com.piggu.finance.api.dto.ReceiptParseRequest;
import com.piggu.finance.api.dto.ReceiptParseResponse;
import com.piggu.finance.domain.CotaDeLeituras;
import com.piggu.finance.integration.LeitorDeRecibos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leitura de recibo por foto. Substitui a acao parse.
 *
 * <p>Este endpoint nao grava nada: devolve os itens lidos para conferencia.</p>
 */
@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final LeitorDeRecibos leitor;
    private final CotaDeLeituras cota;
    private final Consentimentos consentimentos;

    public ReceiptController(LeitorDeRecibos leitor, CotaDeLeituras cota, Consentimentos consentimentos) {
        this.leitor = leitor;
        this.cota = cota;
        this.consentimentos = consentimentos;
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

    /** Retira a autorizacao de enviar a foto ao Gemini; a proxima leitura pergunta de novo. */
    @DeleteMapping("/consent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retirarConsentimento(@AuthUser CurrentUser usuario) {
        consentimentos.retirar(usuario, LeitorDeRecibos.FINALIDADE);
    }
}
