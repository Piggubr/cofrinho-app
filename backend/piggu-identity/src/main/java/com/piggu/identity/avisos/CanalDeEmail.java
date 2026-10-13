package com.piggu.identity.avisos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * E-mail por API HTTP, no formato da Resend (https://resend.com): um POST com from, to,
 * subject e text. Outro provedor com a mesma forma serve trocando a URL.
 */
@Component
public class CanalDeEmail {

    private static final Logger log = LoggerFactory.getLogger(CanalDeEmail.class);

    private final RestClient cliente;
    private final AvisosProperties.Email config;

    public CanalDeEmail(RestClient.Builder builder, AvisosProperties propriedades) {
        this.cliente = builder.build();
        this.config = propriedades.email();
    }

    public boolean ligado() {
        return config.ligado();
    }

    /** @return se o provedor aceitou; falha so vai para o log, o aviso tenta no dia seguinte */
    public boolean enviar(String para, Mensagem mensagem) {
        return enviar(para, mensagem.titulo(), mensagem.texto());
    }

    public boolean enviar(String para, String assunto, String texto) {
        try {
            cliente.post().uri(config.apiUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", config.remetente(), "to", List.of(para),
                            "subject", assunto, "text", texto))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException erro) {
            log.warn("E-mail de aviso nao saiu: {}", erro.getMessage());
            return false;
        }
    }
}
