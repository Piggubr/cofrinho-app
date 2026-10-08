package com.piggu.finance.integration;

import com.piggu.common.dados.Consentimentos;
import com.piggu.common.error.BusinessException;
import com.piggu.common.error.UpstreamException;
import com.piggu.common.security.CurrentUser;
import com.piggu.common.security.PigguRole;
import com.piggu.finance.api.dto.ReceiptParseRequest;
import com.piggu.finance.api.dto.ReceiptParseResponse;
import com.piggu.finance.domain.ProductMemory;
import com.piggu.finance.domain.ProductMemoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Quando o leitor proprio basta e quando o Gemini entra. */
class LeitorDeRecibosTest {

    private static final ReceiptParseRequest FOTO = new ReceiptParseRequest("aGVsbG8=", "image/jpeg");
    private static final String FECHA = "MERCADO\nLEITE NINHO 400G 15,99\nCAFE 3,50\nTOTAL 19,49\n";
    private static final String NAO_FECHA = "MERCADO\nLEITE NINHO 400G 15,99\nTOTAL 19,49\n";

    private final TesseractOcr ocr = mock(TesseractOcr.class);
    private final GeminiReceiptReader gemini = mock(GeminiReceiptReader.class);
    private final ProductMemoryRepository memoria = mock(ProductMemoryRepository.class);
    private final Consentimentos consentimentos = mock(Consentimentos.class);
    private static final CurrentUser USUARIO = new CurrentUser(java.util.UUID.randomUUID(), "b@piggu.test", PigguRole.TITULAR);
    private final LeitorDeRecibos leitor = new LeitorDeRecibos(ocr, gemini, memoria, consentimentos);

    @Test
    @DisplayName("leitura propria que fecha nao chama o Gemini e usa nome e categoria da memoria")
    void proprioBasta() {
        when(ocr.ler(any())).thenReturn(Optional.of(FECHA));
        when(gemini.habilitado()).thenReturn(true);
        when(memoria.findByProductKey(anyString())).thenReturn(Optional.empty());
        when(memoria.findByProductKey("leite ninho")).thenReturn(Optional.of(new ProductMemory(
                "leite ninho", "Leite Ninho 400g", "Alimentação", BigDecimal.ONE, LocalDate.now(), "b@piggu.test")));

        ReceiptParseResponse resposta = leitor.ler(FOTO, USUARIO);

        assertThat(resposta.origem()).isEqualTo("OCR");
        assertThat(resposta.aviso()).isNull();
        assertThat(resposta.itens()).extracting(ReceiptParseResponse.Item::item)
                .containsExactly("Leite Ninho 400g", "Cafe");
        assertThat(resposta.itens()).extracting(ReceiptParseResponse.Item::categoria)
                .containsExactly("Alimentação", "Outros");
        verify(gemini, never()).ler(any());
    }

    @Test
    @DisplayName("leitura que nao fecha vai para o Gemini")
    void naoFechaVaiParaGemini() {
        ReceiptParseResponse doGemini = new ReceiptParseResponse(UUID.randomUUID(), "Mercado", LocalDate.now(),
                List.of(), "GEMINI", null);
        when(ocr.ler(any())).thenReturn(Optional.of(NAO_FECHA));
        when(gemini.habilitado()).thenReturn(true);
        when(gemini.ler(FOTO)).thenReturn(doGemini);

        assertThat(leitor.ler(FOTO, USUARIO)).isSameAs(doGemini);
    }

    @Test
    @DisplayName("com o Gemini fora do ar fica a leitura parcial, com aviso do que conferir")
    void parcialComAviso() {
        when(ocr.ler(any())).thenReturn(Optional.of(NAO_FECHA));
        when(memoria.findByProductKey(anyString())).thenReturn(Optional.empty());
        when(gemini.habilitado()).thenReturn(true);
        when(gemini.ler(FOTO)).thenThrow(new UpstreamException("cota"));

        ReceiptParseResponse resposta = leitor.ler(FOTO, USUARIO);

        assertThat(resposta.origem()).isEqualTo("OCR");
        assertThat(resposta.aviso()).contains("15,99").contains("19,49");
    }

    @Test
    @DisplayName("sem OCR e sem Gemini o erro pede para lancar a mao")
    void nadaDisponivel() {
        when(ocr.ler(any())).thenReturn(Optional.empty());
        when(gemini.habilitado()).thenReturn(false);

        assertThatThrownBy(() -> leitor.ler(FOTO, USUARIO)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("à mão");
    }

    @Test
    @DisplayName("nome em maiusculas do cupom vira nome legivel")
    void capitaliza() {
        assertThat(LeitorDeRecibos.capitalizar("LEITE NINHO 400G")).isEqualTo("Leite Ninho 400g");
    }

    @Test
    @DisplayName("sem autorizacao a foto nao vai ao Gemini: o pedido volta pedindo consentimento")
    void semConsentimentoNaoChamaOGemini() {
        when(ocr.ler(any())).thenReturn(Optional.of(NAO_FECHA));
        when(gemini.habilitado()).thenReturn(true);
        doThrow(new BusinessException("autorize", org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                Consentimentos.CODIGO_NECESSARIO))
                .when(consentimentos).exigir(eq(USUARIO), eq("GEMINI"), anyString(), isNull(), isNull());

        assertThatThrownBy(() -> leitor.ler(FOTO, USUARIO)).isInstanceOf(BusinessException.class);
        verify(gemini, never()).ler(any());
    }
}
