package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.identity.domain.DocumentosDeIdentidade.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Catalogo de documentos por pais: formato e digito verificador de cada um. */
class DocumentosDeIdentidadeTest {

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "PT_NIF, 123 456 789, 123456789",
            "ES_DNI, 12345678-z, 12345678Z",
            "ES_DNI, X1234567L, X1234567L",
            "IT_CF, rssmra85t10a562s, RSSMRA85T10A562S",
            "CL_RUT, 11.111.111-1, 111111111",
            "UY_CI, 1.234.567-2, 12345672",
            "EC_CI, 1710034065, 1710034065",
            "MX_CURP, GOMC800101HDFRRR00, GOMC800101HDFRRR00",
            "AR_DNI, 30.123.456, 30123456",
            "PE_DNI, 12345678, 12345678",
            "CO_CC, 1.020.304.050, 1020304050",
            "VE_CI, V-12.345.678, 12345678",
    })
    @DisplayName("aceita com pontuacao e guarda limpo")
    void validos(Tipo tipo, String digitado, String guardado) {
        assertThat(tipo.normalizar(digitado)).isEqualTo(guardado);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
            "PT_NIF, 123456780",
            "PT_NIF, 423456789",
            "ES_DNI, 12345678A",
            "IT_CF, RSSMRA85T10A562T",
            "CL_RUT, 11.111.111-2",
            "UY_CI, 1.234.567-3",
            "EC_CI, 1710034066",
            "EC_CI, 9910034065",
            "MX_CURP, GOMC800101HDFRRR01",
            "PE_DNI, 1234567",
    })
    @DisplayName("recusa formato errado e digito verificador que nao fecha")
    void invalidos(Tipo tipo, String digitado) {
        assertThatThrownBy(() -> tipo.normalizar(digitado))
                .isInstanceOfSatisfying(BusinessException.class,
                        erro -> assertThat(erro.getCodigo()).isEqualTo("DOCUMENTO_INVALIDO"));
    }

    @Test
    @DisplayName("pais e codigo ISO; Brasil e paises fora do catalogo nao tem documento de fora")
    void paises() {
        assertThat(DocumentosDeIdentidade.pais(" pt ")).isEqualTo("PT");
        assertThatThrownBy(() -> DocumentosDeIdentidade.pais("XX")).isInstanceOf(BusinessException.class);
        assertThat(DocumentosDeIdentidade.pedidoEm("PT")).contains(Tipo.PT_NIF);
        assertThat(DocumentosDeIdentidade.pedidoEm("US")).isEmpty();
        assertThat(DocumentosDeIdentidade.pedidoEm("BR")).isEmpty();
        assertThat(DocumentosDeIdentidade.pedidoEm(null)).isEmpty();
    }
}
