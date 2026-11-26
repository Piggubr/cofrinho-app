package com.piggu.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aviso de "atualize o app" (D-201): o app nativo manda {@code User-Agent: Piggu-Android/2.0.0}
 * ou {@code Piggu-iOS/2.0.0}, e abaixo da versao minima da plataforma a API responde 426 com o
 * codigo {@code ATUALIZE_O_APP}, que abre a tela de atualizar nos dois apps.
 *
 * <p>Desligado por padrao: sem {@code PIGGU_VERSAO_MINIMA_ANDROID} ou
 * {@code PIGGU_VERSAO_MINIMA_IOS}, tudo passa. O site, o app Capacitor antigo e qualquer outro
 * cliente nao mandam esse User-Agent e nunca sao barrados. Compara a versao de mercado
 * ({@code versionName} no Android, {@code MARKETING_VERSION} no iOS), numero a numero.</p>
 */
@Component
public class VersaoMinimaGlobalFilter implements GlobalFilter, Ordered {

    static final String CODIGO = "ATUALIZE_O_APP";
    static final String MENSAGEM = "Esta versão do Piggu não funciona mais. Atualize o app para continuar.";
    private static final Pattern AGENTE = Pattern.compile("^Piggu-(Android|iOS)/(\\d{1,6}(?:\\.\\d{1,6}){0,3})");
    private static final Pattern VERSAO = Pattern.compile("\\d{1,6}(?:\\.\\d{1,6}){0,3}");

    private final int[] minimaAndroid;
    private final int[] minimaIos;

    public VersaoMinimaGlobalFilter(
            @Value("${piggu.versao-minima.android:}") String android,
            @Value("${piggu.versao-minima.ios:}") String ios) {
        this.minimaAndroid = lerConfigurada(android, "PIGGU_VERSAO_MINIMA_ANDROID");
        this.minimaIos = lerConfigurada(ios, "PIGGU_VERSAO_MINIMA_IOS");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange troca, GatewayFilterChain cadeia) {
        if (minimaAndroid == null && minimaIos == null) {
            return cadeia.filter(troca);
        }
        String agente = troca.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
        Matcher partes = agente == null ? null : AGENTE.matcher(agente);
        if (partes == null || !partes.find()) {
            return cadeia.filter(troca);
        }
        int[] minima = "Android".equals(partes.group(1)) ? minimaAndroid : minimaIos;
        if (minima == null || comparar(numeros(partes.group(2)), minima) >= 0) {
            return cadeia.filter(troca);
        }
        return ServicoForaDoAr.escrever(troca.getResponse(), HttpStatus.UPGRADE_REQUIRED,
                CODIGO, MENSAGEM);
    }

    /** Vazio desliga; valor que nao e versao derruba a subida, em vez de barrar todo mundo. */
    static int[] lerConfigurada(String valor, String variavel) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.trim();
        if (!VERSAO.matcher(limpo).matches()) {
            throw new IllegalArgumentException(variavel + " precisa ser uma versao como 2.0.1, veio: " + limpo);
        }
        return numeros(limpo);
    }

    static int[] numeros(String versao) {
        return Arrays.stream(versao.split("\\.")).mapToInt(Integer::parseInt).toArray();
    }

    /** 2.0 e 2.0.0 sao iguais: a parte que falta conta como zero. */
    static int comparar(int[] a, int[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int diferenca = Integer.compare(i < a.length ? a[i] : 0, i < b.length ? b[i] : 0);
            if (diferenca != 0) {
                return diferenca;
            }
        }
        return 0;
    }

    /** Depois da correlacao e do limite por IP: a resposta leva X-Request-Id e conta no teto. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
