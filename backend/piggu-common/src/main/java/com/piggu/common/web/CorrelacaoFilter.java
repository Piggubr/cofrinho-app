package com.piggu.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Amarra todas as linhas de log de uma requisicao.
 *
 * <p>Le o {@code X-Request-Id} que o gateway gerou (ou cria um, se a chamada veio
 * direto), poe no MDC junto com o id do usuario e devolve no cabecalho da resposta.
 * Assim um erro relatado pelo front acha, nos logs de todos os servicos, tudo o que
 * aconteceu naquela chamada.</p>
 *
 * <p>Vai para o log o <em>padrao</em> da rota ({@code /api/expenses/{id}}), nunca o
 * caminho concreto, que pode carregar e-mail. Usuario e so o UUID: nada de e-mail,
 * nome ou token.</p>
 */
public class CorrelacaoFilter extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Request-Id";
    public static final String MDC_REQUISICAO = "requestId";
    public static final String MDC_USUARIO = "userId";

    /** Id vindo de fora so e aceito se for curto e sem caracteres que quebrem o log. */
    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Logger acesso = LoggerFactory.getLogger("com.piggu.acesso");

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        String recebido = pedido.getHeader(CABECALHO);
        String id = recebido != null && VALIDO.matcher(recebido).matches() ? recebido : UUID.randomUUID().toString();

        MDC.put(MDC_REQUISICAO, id);
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt) {
            MDC.put(MDC_USUARIO, jwt.getSubject());
        }
        resposta.setHeader(CABECALHO, id);

        long inicio = System.nanoTime();
        try {
            cadeia.doFilter(pedido, resposta);
        } finally {
            if (!pedido.getRequestURI().startsWith("/actuator")) {
                Object rota = pedido.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                acesso.info("{} {} -> {} em {} ms", pedido.getMethod(), rota == null ? "(sem rota)" : rota,
                        resposta.getStatus(), (System.nanoTime() - inicio) / 1_000_000);
            }
            MDC.remove(MDC_REQUISICAO);
            MDC.remove(MDC_USUARIO);
        }
    }
}
