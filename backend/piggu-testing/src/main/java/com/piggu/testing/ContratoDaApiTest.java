package com.piggu.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato da API de um servico, guardado em {@code docs/api/<servico>.yaml}.
 *
 * <p>Os apps nativos (iOS e Android) falam direto com a API e nao sao atualizados junto com
 * o servidor. Por isso a regra: a API nunca remove nem renomeia campo, so acrescenta. Este
 * teste gera o contrato a partir dos controllers e compara com o arquivo commitado; qualquer
 * diferenca quebra o build ate alguem regravar o arquivo e conferir o diff no PR.</p>
 *
 * <p>Para regravar: {@code ./scripts/regravar-contrato.sh} (de dentro de backend/), que roda so
 * os testes de contrato com {@code -Dpiggu.regravar-contrato=true}.</p>
 */
@AutoConfigureMockMvc
public abstract class ContratoDaApiTest extends PostgresIntegrationTest {

    /** Liga a regravacao dos arquivos em vez da comparacao. */
    public static final String REGRAVAR = "piggu.regravar-contrato";

    /** Pasta docs/api, passada pelo Surefire (POM pai); sem ela, relativa ao modulo. */
    public static final String PASTA = "piggu.contrato.pasta";

    @Autowired
    private MockMvc mockMvc;

    /** Nome do arquivo em docs/api, sem extensao: identity, finance... */
    protected abstract String servico();

    @Test
    @DisplayName("o contrato gerado do codigo e o mesmo de docs/api")
    void contratoNaoMudou() throws Exception {
        String gerado = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        gerado = gerado.endsWith("\n") ? gerado : gerado + "\n";
        Path arquivo = arquivo();

        if (Boolean.getBoolean(REGRAVAR)) {
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, gerado, StandardCharsets.UTF_8);
            return;
        }
        // No Windows o Git pode trazer o arquivo com CRLF; o conteudo e o mesmo.
        String commitado = Files.exists(arquivo)
                ? Files.readString(arquivo, StandardCharsets.UTF_8).replace("\r\n", "\n")
                : "";
        if (!gerado.equals(commitado)) {
            fail("o contrato da API mudou: rode %s para regravar e confira se só acrescentou campos (%s, linha %d)"
                    .formatted(comando(), arquivo.normalize(), primeiraDiferenca(gerado, commitado)));
        }
    }

    @Test
    @DisplayName("fora do perfil dev e dos testes, /v3/api-docs e o Swagger UI ficam desligados")
    void desligadoEmProducao() throws IOException {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties padrao = yaml.getObject();

        assertThat(padrao).isNotNull();
        assertThat(padrao.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(padrao.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    private static int primeiraDiferenca(String gerado, String commitado) {
        String[] novas = gerado.split("\n", -1);
        String[] velhas = commitado.split("\n", -1);
        int linha = 0;
        while (linha < novas.length && linha < velhas.length && novas[linha].equals(velhas[linha])) {
            linha++;
        }
        return linha + 1;
    }

    private Path arquivo() {
        String pasta = System.getProperty(PASTA, "../../docs/api");
        return Path.of(pasta, servico() + ".yaml");
    }

    private String comando() {
        return "cd backend && ./scripts/regravar-contrato.sh " + servico();
    }
}
