package com.piggu.identity.domain;

import com.piggu.common.error.BusinessException;
import com.piggu.common.web.LimiteDeRequisicoes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O "CPF ja esta em outra conta" diz que o CPF tem conta no Piggu. A resposta fica (quem e
 * dono precisa saber por que nao entra), mas so algumas vezes por dia para a mesma conta e o
 * mesmo IP: ninguem varre uma lista de CPFs vazados (D-160).
 *
 * <p>shortcut: em memoria, por instancia, como as travas de login; com mais de uma instancia,
 * passar para o banco.</p>
 */
@Component
public class TentativasDeCpf {

    static final int MAXIMO = 3;
    static final Duration JANELA = Duration.ofHours(24);
    private static final int CHAVES_MAXIMAS = 50_000;

    private final Map<String, List<Instant>> recusas = new ConcurrentHashMap<>();
    private final LimiteDeRequisicoes.Limites limites;
    private final Clock relogio;

    @Autowired
    public TentativasDeCpf(LimiteDeRequisicoes.Limites limites) {
        this(limites, Clock.systemUTC());
    }

    TentativasDeCpf(LimiteDeRequisicoes.Limites limites, Clock relogio) {
        this.limites = limites;
        this.relogio = relogio;
    }

    /** Antes de procurar o CPF: quem ja levou {@link #MAXIMO} recusas na janela para aqui. */
    public void exigirTentativa(UUID conta) {
        Instant agora = relogio.instant();
        for (String chave : chaves(conta)) {
            if (recentes(chave, agora) >= MAXIMO) {
                throw new BusinessException("Muitas tentativas com CPF que já está em outra conta. Tente de novo amanhã"
                        + " ou fale com a gente pelo canal de privacidade.", HttpStatus.TOO_MANY_REQUESTS, "CPF_TENTATIVAS");
            }
        }
    }

    public void recusado(UUID conta) {
        Instant agora = relogio.instant();
        if (recusas.size() > CHAVES_MAXIMAS) {
            recusas.entrySet().removeIf(entrada -> recentes(entrada.getKey(), agora) == 0);
        }
        for (String chave : chaves(conta)) {
            recusas.compute(chave, (k, lista) -> {
                List<Instant> nova = lista == null ? new ArrayList<>() : new ArrayList<>(lista);
                nova.removeIf(quando -> quando.isBefore(agora.minus(JANELA)));
                nova.add(agora);
                return nova;
            });
        }
    }

    private long recentes(String chave, Instant agora) {
        List<Instant> lista = recusas.get(chave);
        return lista == null ? 0 : lista.stream().filter(quando -> !quando.isBefore(agora.minus(JANELA))).count();
    }

    private List<String> chaves(UUID conta) {
        List<String> chaves = new ArrayList<>(List.of("conta:" + conta));
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            HttpServletRequest pedido = atributos.getRequest();
            chaves.add("ip:" + LimiteDeRequisicoes.ipDeOrigem(pedido, limites));
        }
        return chaves;
    }
}
