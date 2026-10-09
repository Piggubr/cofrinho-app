package com.piggu.common.web;

import com.piggu.common.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anti-abuso: quantas chamadas por minuto e quanto corpo cada uma pode ter.
 *
 * <p>Roda duas vezes. <b>Antes</b> da autenticacao, por IP, nas rotas abertas (login,
 * renovacao, avisos de pagamento): barra a enxurrada sem gastar uma verificacao RS256
 * por tentativa. <b>Depois</b> da autenticacao, por conta, em toda escrita, envio de
 * arquivo e busca que custa (servico externo ou busca no banco): quem tem token valido
 * tambem nao martela o servico, a IA nem as APIs de fora.</p>
 *
 * <p>O IP vem do {@code X-Forwarded-For} contado <em>da direita</em>: o elemento mais a
 * direita foi escrito pelo proxy mais proximo, e so ele e confiavel. O que o cliente
 * manda fica a esquerda e e ignorado. {@code proxiesNaFrente} acompanha a topologia: 1
 * com so o gateway, 2 com o Caddy na frente dele.</p>
 *
 * <p>ponytail: contador em memoria por instancia. Com mais de uma replica de cada
 * servico o limite real vira N vezes o configurado; ai mover para o Redis.</p>
 */
public class LimiteDeRequisicoes extends OncePerRequestFilter {

    /** Quando cada instancia do filtro age. */
    public enum Etapa { ANTES_DO_LOGIN, DEPOIS_DO_LOGIN }

    /**
     * @param login           chamadas por minuto, por IP, em /api/auth/google e /api/auth/refresh
     * @param publico         chamadas por minuto, por IP, nas demais rotas abertas (webhooks)
     * @param escrita         POST/PUT/PATCH/DELETE por minuto, por conta
     * @param envio           envios de foto ou extrato por minuto, por conta
     * @param consulta        buscas que custam (TMDB, catalogo, cambio, busca global) por minuto, por conta
     * @param corpoMaximo     bytes de corpo numa chamada comum
     * @param envioMaximo     bytes de corpo num envio de foto ou extrato (5 MB em base64 cabem)
     * @param proxiesNaFrente quantos proxies confiaveis escrevem no X-Forwarded-For
     */
    @ConfigurationProperties(prefix = "piggu.limite")
    public record Limites(@DefaultValue("20") int login,
                          @DefaultValue("120") int publico,
                          @DefaultValue("120") int escrita,
                          @DefaultValue("15") int envio,
                          @DefaultValue("60") int consulta,
                          @DefaultValue("262144") long corpoMaximo,
                          @DefaultValue("8388608") long envioMaximo,
                          @DefaultValue("1") int proxiesNaFrente) {
    }

    private static final Logger log = LoggerFactory.getLogger(LimiteDeRequisicoes.class);
    private static final long MINUTO = 60_000;
    private static final int CHAVES_ANTES_DE_LIMPAR = 50_000;

    private final Etapa etapa;
    private final Limites limites;
    private final ObjectMapper json;
    private final Clock relogio;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    public LimiteDeRequisicoes(Etapa etapa, Limites limites, ObjectMapper json, Clock relogio) {
        this.etapa = etapa;
        this.limites = limites;
        this.json = json;
        this.relogio = relogio;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        String caminho = pedido.getRequestURI();
        boolean escrita = !HttpMethod.GET.matches(pedido.getMethod()) && !HttpMethod.HEAD.matches(pedido.getMethod())
                && !HttpMethod.OPTIONS.matches(pedido.getMethod());
        boolean envio = escrita && ehEnvioDeArquivo(caminho);
        boolean consulta = !escrita && ehConsultaQueCusta(caminho);

        if (etapa == Etapa.ANTES_DO_LOGIN && escrita) {
            long maximo = envio ? limites.envioMaximo() : limites.corpoMaximo();
            if (pedido.getContentLengthLong() > maximo) {
                recusar(resposta, HttpStatus.CONTENT_TOO_LARGE, "O envio e grande demais.", "CORPO_GRANDE_DEMAIS", 0);
                return;
            }
        }

        String chave = null;
        int porMinuto = 0;
        if (etapa == Etapa.ANTES_DO_LOGIN && ehRotaAberta(caminho)) {
            boolean login = caminho.startsWith("/api/auth/");
            chave = (login ? "login:" : "aberta:") + ipDeOrigem(pedido);
            porMinuto = login ? limites.login() : limites.publico();
        } else if (etapa == Etapa.DEPOIS_DO_LOGIN && (escrita || consulta)) {
            String conta = contaLogada();
            if (conta != null) {
                chave = (envio ? "envio:" : consulta ? "consulta:" : "escrita:") + conta;
                porMinuto = envio ? limites.envio() : consulta ? limites.consulta() : limites.escrita();
            }
        }

        if (chave != null) {
            long agora = relogio.millis();
            Janela janela = janelas.compute(chave, (k, atual) ->
                    atual == null || agora - atual.inicio >= MINUTO ? new Janela(agora) : atual.contar());
            if (janela.chamadas > porMinuto) {
                long espera = Math.max(1, (janela.inicio + MINUTO - agora) / 1000);
                log.warn("Limite de chamadas atingido: tipo={} rota={}", chave.substring(0, chave.indexOf(':')), caminho);
                recusar(resposta, HttpStatus.TOO_MANY_REQUESTS,
                        "Muitas tentativas seguidas. Espere um pouco e tente de novo.", "LIMITE_DE_CHAMADAS", espera);
                return;
            }
            if (janelas.size() > CHAVES_ANTES_DE_LIMPAR) {
                janelas.values().removeIf(antiga -> agora - antiga.inicio >= MINUTO);
            }
        }
        cadeia.doFilter(pedido, resposta);
    }

    /** IP de quem chamou, lido da direita do X-Forwarded-For (ver javadoc da classe). */
    String ipDeOrigem(HttpServletRequest pedido) {
        String cabecalho = pedido.getHeader("X-Forwarded-For");
        if (cabecalho == null || cabecalho.isBlank()) {
            return pedido.getRemoteAddr();
        }
        String[] saltos = cabecalho.split(",");
        int indice = saltos.length - limites.proxiesNaFrente();
        return indice >= 0 ? saltos[indice].trim() : saltos[0].trim();
    }

    private static boolean ehRotaAberta(String caminho) {
        return caminho.equals("/api/auth/google") || caminho.equals("/api/auth/refresh")
                || caminho.equals("/api/auth/logout") || caminho.startsWith("/api/billing/webhooks/")
                || caminho.startsWith("/api/billing/stores/");
    }

    /**
     * Rotas que recebem foto em base64 ou extrato de banco: corpo maior e limite proprio,
     * porque custam storage, IA ou leitura de milhares de linhas.
     */
    private static boolean ehEnvioDeArquivo(String caminho) {
        return caminho.equals("/api/assets") || caminho.equals("/api/feed") || caminho.equals("/api/receipts/parse")
                || caminho.startsWith("/api/places") || caminho.startsWith("/api/expenses/import");
    }

    /** Leituras que chamam servico de fora (TMDB, Open Food Facts, cambio) ou varrem o banco. */
    private static boolean ehConsultaQueCusta(String caminho) {
        return caminho.equals("/api/movies/search") || caminho.equals("/api/movies/random")
                || caminho.equals("/api/shopping/catalog") || caminho.startsWith("/api/exchange-rate")
                || caminho.equals("/api/expenses/search") || caminho.equals("/api/products/price-check");
    }

    private static String contaLogada() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt ? jwt.getSubject() : null;
    }

    private void recusar(HttpServletResponse resposta, HttpStatus status, String mensagem, String codigo, long espera)
            throws IOException {
        resposta.setStatus(status.value());
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        if (espera > 0) {
            resposta.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(espera));
        }
        json.writeValue(resposta.getOutputStream(), ApiError.de(mensagem, codigo));
    }

    private static final class Janela {
        private final long inicio;
        private int chamadas = 1;

        private Janela(long inicio) {
            this.inicio = inicio;
        }

        private Janela contar() {
            chamadas++;
            return this;
        }
    }
}
