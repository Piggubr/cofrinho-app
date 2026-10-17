package com.piggu.identity.avisos;

import com.piggu.identity.config.DadosProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

/** GET /api/alerts do financeiro, com o token curto da pessoa. */
@Component
public class AvisosDoFinanceiro {

    private final RestClient cliente;
    private final String url;

    public AvisosDoFinanceiro(RestClient.Builder builder, DadosProperties servicos) {
        this.cliente = builder.build();
        this.url = servicos.servicos().get("finance");
    }

    public boolean ligado() {
        return url != null;
    }

    public List<Aviso> deHoje(String token) {
        Aviso[] avisos = cliente.get().uri(url + "/api/alerts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(Aviso[].class);
        return avisos == null ? List.of() : Arrays.asList(avisos);
    }
}
