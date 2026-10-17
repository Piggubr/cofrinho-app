package com.piggu.finance.domain;

import com.piggu.common.web.Texto;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normaliza o nome de um produto para servir de chave na memoria de precos.
 *
 * <p>Porte direto de chaveProduto_ do Apps Script: "Leite Meio Gordo 1L (Mimosa)"
 * e "leite meio gordo 1 l" precisam cair na mesma chave para que o historico de
 * preco seja o mesmo produto. A ordem das limpezas importa e foi preservada.</p>
 */
public final class ChaveProduto {

    private static final Pattern PARENTESES = Pattern.compile("\\([^)]*\\)");
    private static final Pattern UNIDADES =
            Pattern.compile("\\b\\d+[.,]?\\d*\\s*(kg|g|mg|l|ml|cl|un|uni|unidades?)\\b");
    private static final Pattern ARTIGOS = Pattern.compile("\\b(de|da|do|das|dos|um|uma)\\b");
    private static final Pattern NAO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");

    private ChaveProduto() {
    }

    public static String de(String nome) {
        if (Texto.vazio(nome)) {
            return "";
        }
        String chave = Texto.semAcento(nome.toLowerCase(Locale.ROOT));
        chave = PARENTESES.matcher(chave).replaceAll(" ");
        chave = UNIDADES.matcher(chave).replaceAll(" ");
        chave = ARTIGOS.matcher(chave).replaceAll(" ");
        chave = NAO_ALFANUMERICO.matcher(chave).replaceAll(" ");
        return Texto.limitar(Texto.espacoUnico(chave), 200);
    }
}
