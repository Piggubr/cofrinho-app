package com.piggu.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Teto de chamadas por IP na porta de entrada, valendo para a API inteira.
 *
 * <p>Os servicos ja limitam o que custa caro ({@code LimiteDeRequisicoes} no piggu-common:
 * login por IP, escrita e envio por conta). Faltava o resto: leitura sem teto, chamada sem
 * token para qualquer servico e varredura de enderecos. Este filtro corta a enxurrada antes
 * de ela chegar a um servico. O teto e folgado de proposito: no celular muita gente sai pelo
 * mesmo IP (CGNAT da operadora), e uma tela do app faz varias chamadas de uma vez.</p>
 *
 * <p>O IP e o elemento mais a direita do {@code X-Forwarded-For}: em producao o Caddy
 * descarta o que o cliente mandou e escreve o IP real, e o gateway so escuta na propria
 * maquina. Sem o cabecalho (desenvolvimento), vale o endereco da conexao.</p>
 *
 * <p>ponytail: contador em memoria, como nos servicos. Com mais de um gateway, mover para o Redis.</p>
 */
@Component
public class LimitePorIpGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LimitePorIpGlobalFilter.class);
    private static final long MINUTO = 60_000;
    private static final int CHAVES_ANTES_DE_LIMPAR = 50_000;

    private final int porMinuto;
    private final Clock relogio;
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    @Autowired
    public LimitePorIpGlobalFilter(@Value("${piggu.limite.gateway-por-minuto:600}") int porMinuto) {
        this(porMinuto, Clock.systemUTC());
    }

    LimitePorIpGlobalFilter(int porMinuto, Clock relogio) {
        this.porMinuto = porMinuto;
        this.relogio = relogio;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange troca, GatewayFilterChain cadeia) {
        if (porMinuto <= 0) {
            return cadeia.filter(troca);
        }
        long agora = relogio.millis();
        Janela janela = janelas.compute(ipDeOrigem(troca.getRequest()), (ip, atual) ->
                atual == null || agora - atual.inicio >= MINUTO ? new Janela(agora) : atual.contar());
        if (janelas.size() > CHAVES_ANTES_DE_LIMPAR) {
            janelas.values().removeIf(antiga -> agora - antiga.inicio >= MINUTO);
        }
        if (janela.chamadas <= porMinuto) {
            return cadeia.filter(troca);
        }
        // So na primeira recusa da janela: uma enxurrada nao vira uma enxurrada de linhas no log.
        if (janela.chamadas == porMinuto + 1) {
            log.warn("Limite de chamadas por IP atingido no gateway ({} por minuto)", porMinuto);
        }
        return recusar(troca.getResponse(), Math.max(1, (janela.inicio + MINUTO - agora) / 1000));
    }

    static String ipDeOrigem(ServerHttpRequest pedido) {
        String cabecalho = pedido.getHeaders().getFirst("X-Forwarded-For");
        if (cabecalho != null && !cabecalho.isBlank()) {
            String[] saltos = cabecalho.split(",");
            return porRede(saltos[saltos.length - 1].trim());
        }
        InetSocketAddress conexao = pedido.getRemoteAddress();
        return conexao == null || conexao.getAddress() == null ? "-" : porRede(conexao.getAddress().getHostAddress());
    }

    /**
     * IPv6 conta por /64: um cliente IPv6 costuma ter o /64 inteiro e trocaria de endereco a
     * cada pedido para escapar do limite (D-161).
     */
    static String porRede(String ip) {
        if (ip == null || !ip.contains(":") || !ip.matches("[0-9A-Fa-f:.]+")) {
            return ip;
        }
        try {
            byte[] bytes = java.net.InetAddress.getByName(ip).getAddress();
            if (bytes.length != 16) {
                return ip;
            }
            StringBuilder rede = new StringBuilder();
            for (int i = 0; i < 8; i += 2) {
                rede.append(Integer.toHexString(((bytes[i] & 0xff) << 8) | (bytes[i + 1] & 0xff))).append(':');
            }
            return rede.append(":/64").toString();
        } catch (java.net.UnknownHostException invalido) {
            return ip;
        }
    }

    /** Mesmo corpo de erro dos servicos (ApiError), que o front ja sabe mostrar. */
    private static Mono<Void> recusar(ServerHttpResponse resposta, long espera) {
        resposta.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        resposta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        resposta.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(espera));
        String corpo = "{\"erro\":\"Muitas chamadas seguidas. Espere um pouco e tente de novo.\","
                + "\"codigo\":\"LIMITE_DE_CHAMADAS\",\"campos\":[],\"momento\":\"" + Instant.now() + "\"}";
        DataBuffer buffer = resposta.bufferFactory().wrap(corpo.getBytes(StandardCharsets.UTF_8));
        return resposta.writeWith(Mono.just(buffer));
    }

    /** Logo depois da correlacao, para a recusa tambem levar X-Request-Id e linha de acesso. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
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
