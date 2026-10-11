package com.piggu.common;

import com.piggu.common.auditoria.HistoricoController;
import com.piggu.common.auditoria.TrilhaDeAuditoria;
import com.piggu.common.dados.DadosDaFamilia;
import com.piggu.common.dados.MeusDadosController;
import com.piggu.common.error.ApiExceptionHandler;
import com.piggu.common.security.FamiliaAtual;
import com.piggu.common.security.ResourceServerSecurityConfig;
import com.piggu.common.web.CorrelacaoFilter;
import com.piggu.common.web.LimiteDeRequisicoes;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * Liga o tratamento de erros, a seguranca padrao e a correlacao de logs assim que o
 * servico declara a dependencia {@code piggu-common} — sem precisar de {@code @Import}
 * em cada aplicacao.
 */
@AutoConfiguration
@EnableConfigurationProperties(LimiteDeRequisicoes.Limites.class)
@Import({ApiExceptionHandler.class, ResourceServerSecurityConfig.class})
public class PigguCommonAutoConfiguration {

    /** Logo depois da seguranca, para ja saber quem esta chamando. */
    @Bean
    public FilterRegistrationBean<CorrelacaoFilter> correlacaoFilter() {
        FilterRegistrationBean<CorrelacaoFilter> registro = new FilterRegistrationBean<>(new CorrelacaoFilter());
        registro.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
        return registro;
    }

    /** Por IP nas rotas abertas, antes de gastar uma verificacao de token por tentativa. */
    @Bean
    public FilterRegistrationBean<LimiteDeRequisicoes> limiteAntesDoLogin(LimiteDeRequisicoes.Limites limites,
                                                                          ObjectMapper json) {
        FilterRegistrationBean<LimiteDeRequisicoes> registro = new FilterRegistrationBean<>(
                new LimiteDeRequisicoes(LimiteDeRequisicoes.Etapa.ANTES_DO_LOGIN, limites, json, Clock.systemUTC()));
        registro.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1);
        return registro;
    }

    /** Por conta, em toda escrita e envio de foto. */
    @Bean
    public FilterRegistrationBean<LimiteDeRequisicoes> limiteDepoisDoLogin(LimiteDeRequisicoes.Limites limites,
                                                                           ObjectMapper json) {
        FilterRegistrationBean<LimiteDeRequisicoes> registro = new FilterRegistrationBean<>(
                new LimiteDeRequisicoes(LimiteDeRequisicoes.Etapa.DEPOIS_DO_LOGIN, limites, json, Clock.systemUTC()));
        registro.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 2);
        return registro;
    }

    /** Com PIGGU_AMBIENTE=producao, senha fraca do banco derruba a subida. */
    @Bean
    @ConditionalOnProperty(name = "piggu.ambiente", havingValue = "producao")
    ProtecaoDeProducao protecaoDeProducao(Environment ambiente) {
        return new ProtecaoDeProducao(ambiente);
    }

    /** Servico que declara onde guarda dado de pessoas ganha as rotas de exportar e apagar. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(DadosDaFamilia.class)
    static class RotasDosDadosDoTitular {

        @Bean
        MeusDadosController meusDadosController(DadosDaFamilia dados) {
            return new MeusDadosController(dados);
        }
    }

    /** Servico que guarda a trilha de auditoria ganha a rota do historico. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(TrilhaDeAuditoria.class)
    static class RotaDoHistorico {

        @Bean
        HistoricoController historicoController(TrilhaDeAuditoria trilha) {
            return new HistoricoController(trilha);
        }
    }

    /** Todo servico com JPA ganha o filtro por familia (ver {@link FamiliaAtual}). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.hibernate.context.spi.CurrentTenantIdentifierResolver")
    static class FiltroPorFamilia {

        @Bean
        HibernatePropertiesCustomizer familiaAtualNoHibernate() {
            return propriedades -> propriedades.put("hibernate.tenant_identifier_resolver", new FamiliaAtual());
        }
    }
}
