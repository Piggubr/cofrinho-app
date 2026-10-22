package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.Locale;
import java.util.Set;

/**
 * Onde a pessoa mora. O Piggu e brasileiro: o documento e sempre o CPF ({@link Cpf}),
 * more a pessoa aqui ou fora. O pais fica para o DDI do celular e para saber quem mora fora.
 *
 * <p>Contas antigas podem ter o documento de outro pais (colunas {@code id_doc_*}, do
 * tempo em que o cadastro pedia NIF, DNI e afins); ele so sai na exportacao dos dados.
 * O front tem a mesma lista em {@code core/ui/documentos.ts}.</p>
 */
public final class Paises {

    public static final String BRASIL = "BR";

    private static final Set<String> PAISES = Set.of(Locale.getISOCountries());

    private Paises() {
    }

    /** Codigo ISO 3166 de duas letras, em maiusculas; recusa o que nao e pais. */
    public static String pais(String digitado) {
        String codigo = digitado == null ? "" : digitado.trim().toUpperCase(Locale.ROOT);
        if (!PAISES.contains(codigo)) {
            throw new BusinessException("Escolha o país onde você mora.", HttpStatus.UNPROCESSABLE_CONTENT,
                    "PAIS_INVALIDO");
        }
        return codigo;
    }
}
