package com.piggu.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Recorte do que a pessoa ja ve, escolhido no painel: tudo (padrao), so o que ela lancou
 * ("meu") ou so o compartilhado ("casal").
 *
 * <p>Vem do parametro {@code visao} da requisicao, entao qualquer lista ou soma de gastos e
 * receitas aceita o recorte sem cada endpoint repetir a regra. Como o {@link DonoAtual}, so
 * diminui o que aparece: nao ha valor que mostre algo que o filtro de dono esconde.</p>
 */
public final class VisaoAtual {

    public static final String FILTRO = "visao";
    public static final String PARAMETRO = "visao";
    public static final String MODO = "modo";
    public static final String EU = "eu";

    public static final String TUDO = "TUDO";
    public static final String MEU = "MEU";
    public static final String CASAL = "CASAL";

    /** Condicao para tabelas com {@code user_id} e {@code shared}. */
    public static final String CONDICAO = "(:modo = 'TUDO'"
            + " OR (:modo = 'MEU' AND {alias}.user_id = CAST(:eu AS uuid))"
            + " OR (:modo = 'CASAL' AND {alias}.shared))";

    private VisaoAtual() {
    }

    /** Modo pedido; fora de uma requisicao (job) ou com valor desconhecido, tudo. */
    public static final class Modo implements Supplier<String> {
        @Override
        public String get() {
            if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
                return TUDO;
            }
            HttpServletRequest requisicao = atributos.getRequest();
            String pedido = requisicao.getParameter(PARAMETRO);
            if (pedido == null) {
                return TUDO;
            }
            return switch (pedido.trim().toUpperCase(Locale.ROOT)) {
                case MEU -> MEU;
                case CASAL -> CASAL;
                default -> TUDO;
            };
        }
    }
}
