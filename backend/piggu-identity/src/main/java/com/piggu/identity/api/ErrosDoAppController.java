package com.piggu.identity.api;

import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * Erros de JavaScript que aconteceram no aparelho de alguem.
 *
 * <p>O backend ja registra as proprias falhas; as do app so se viam no console do navegador
 * da pessoa. O front manda aqui o que quebrou (sem token: funciona antes do login e nao entra
 * no ciclo de renovacao da sessao), e a linha vai para o log com o logger
 * {@code com.piggu.erros-do-app}, de onde o painel "Erros" a le.</p>
 *
 * <p>LGPD: o texto vem do aparelho e pode trazer qualquer coisa. Tudo passa por
 * {@link #limpar}, que tira e-mail, CPF, telefone, numero comprido, token e o que vem depois de "?"
 * num endereco, e corta no tamanho. Nao vai id de conta: a chamada e anonima. O limite por
 * IP das rotas abertas (LimiteDeRequisicoes) segura quem tentar encher o log.</p>
 */
@RestController
public class ErrosDoAppController {

    static final String ROTA = "/api/app/erros";
    static final String CONTADOR = "piggu.erros.do.app";

    private static final Logger log = LoggerFactory.getLogger("com.piggu.erros-do-app");

    private static final Pattern EMAIL = Pattern.compile("[\\w.%+-]+@[\\w-]+\\.[\\w.-]*[\\w-]");
    private static final Pattern JWT = Pattern.compile("eyJ[\\w-]*\\.[\\w-]+\\.[\\w-]*");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+\\S+");
    private static final Pattern CPF = Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b");
    private static final Pattern TELEFONE = Pattern.compile("\\(?\\b\\d{2}\\)?\\s?\\d{4,5}-\\d{4}\\b");
    private static final Pattern NUMERO_COMPRIDO = Pattern.compile("\\d{8,}");
    private static final Pattern BUSCA_NA_URL = Pattern.compile("\\?[^\\s\"')]+");
    private static final Pattern QUEBRA_DE_LINHA = Pattern.compile("[\\r\\n]+");
    private static final Pattern CONTROLE = Pattern.compile("\\p{Cntrl}");

    /**
     * @param mensagem a mensagem do erro ({@code error.message})
     * @param pilha    a pilha de chamadas, como o navegador escreveu
     * @param tela     o caminho da tela, com os ids ja trocados pelo front
     * @param versao   a versao do app, quando houver
     */
    public record ErroDoApp(String mensagem, String pilha, String tela, String versao) {
    }

    @PostMapping(ROTA)
    public ResponseEntity<Void> registrar(@RequestBody ErroDoApp erro) {
        log.atWarn()
                .addKeyValue("app.tela", limpar(erro.tela(), 120))
                .addKeyValue("app.versao", limpar(erro.versao(), 40))
                .addKeyValue("app.pilha", limpar(erro.pilha(), 2000))
                .log("Erro no app: {}", limpar(erro.mensagem(), 300));
        Metrics.counter(CONTADOR).increment();
        return ResponseEntity.noContent().build();
    }

    /** Tira dado pessoal e caractere que quebraria o log, e corta em {@code limite}. */
    static String limpar(String texto, int limite) {
        if (texto == null || texto.isBlank()) {
            return "-";
        }
        // Limpa antes de cortar: cortar primeiro deixaria meio e-mail, que o padrao nao reconhece.
        String limpo = EMAIL.matcher(texto.length() > 50_000 ? texto.substring(0, 50_000) : texto).replaceAll("[email]");
        limpo = JWT.matcher(limpo).replaceAll("[token]");
        limpo = BEARER.matcher(limpo).replaceAll("Bearer [token]");
        limpo = BUSCA_NA_URL.matcher(limpo).replaceAll("?[...]");
        limpo = CPF.matcher(limpo).replaceAll("[cpf]");
        limpo = TELEFONE.matcher(limpo).replaceAll("[telefone]");
        limpo = NUMERO_COMPRIDO.matcher(limpo).replaceAll("[numero]");
        limpo = QUEBRA_DE_LINHA.matcher(limpo).replaceAll(" | ");
        limpo = CONTROLE.matcher(limpo).replaceAll(" ").trim();
        return limpo.length() <= limite ? limpo : limpo.substring(0, limite);
    }
}
