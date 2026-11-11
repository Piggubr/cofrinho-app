package com.piggu.finance.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.security.FamiliaAtual;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quem e da familia, pelo que o finance ja viu (BUG-022, D-192).
 *
 * <p>A lista de membros mora no identity e o finance nao chama outro servico no caminho de
 * uma requisicao. Entao cada chamada com token grava a pessoa na familia do token: quem
 * aceitou o convite ja abriu o app e ja passou por aqui. O {@code dividirCom} so aceita
 * essas pessoas.</p>
 */
@Component
public class PessoasDaFamilia implements HandlerInterceptor {

    private static final int CHAVES_ANTES_DE_LIMPAR = 50_000;

    private final JdbcTemplate jdbc;
    /** Ja gravadas neste processo: uma escrita por pessoa, nao uma por chamada. */
    private final Set<String> gravadas = ConcurrentHashMap.newKeySet();

    public PessoasDaFamilia(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean preHandle(HttpServletRequest pedido, HttpServletResponse resposta, Object handler) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof Jwt jwt && jwt.getSubject() != null) {
            UUID familia = FamiliaAtual.de(jwt);
            if (!FamiliaAtual.NENHUMA.equals(familia)) {
                registrar(familia, UUID.fromString(jwt.getSubject()));
            }
        }
        return true;
    }

    void registrar(UUID familia, UUID pessoa) {
        String chave = familia + ":" + pessoa;
        if (gravadas.contains(chave)) {
            return;
        }
        jdbc.update("INSERT INTO household_people (household_id, user_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                familia, pessoa);
        if (gravadas.size() > CHAVES_ANTES_DE_LIMPAR) {
            gravadas.clear();
        }
        gravadas.add(chave);
    }

    /** Recusa com 400 se algum id nao e de pessoa desta familia. */
    public void exigirDaFamilia(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<UUID> conhecidas = jdbc.queryForList(
                "SELECT user_id FROM household_people WHERE household_id = ? AND user_id = ANY(?)",
                UUID.class, FamiliaAtual.atual(), ids.toArray(UUID[]::new));
        if (conhecidas.size() < Set.copyOf(ids).size()) {
            throw new BusinessException("Só dá para dividir com pessoas da sua família.", HttpStatus.BAD_REQUEST,
                    "VALIDACAO");
        }
    }
}
