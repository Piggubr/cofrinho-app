package com.piggu.common;

import com.piggu.common.dados.DadosDaFamilia;
import com.piggu.common.dados.MeusDadosController;
import com.piggu.common.error.ApiExceptionHandler;
import com.piggu.common.security.FamiliaAtual;
import com.piggu.common.security.ResourceServerSecurityConfig;
import com.piggu.common.web.CorrelacaoFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Liga o tratamento de erros, a seguranca padrao e a correlacao de logs assim que o
 * servico declara a dependencia {@code piggu-common} — sem precisar de {@code @Import}
 * em cada aplicacao.
 */
@AutoConfiguration
@Import({ApiExceptionHandler.class, ResourceServerSecurityConfig.class})
public class PigguCommonAutoConfiguration {

    /** Logo depois da seguranca, para ja saber quem esta chamando. */
    @Bean
    public FilterRegistrationBean<CorrelacaoFilter> correlacaoFilter() {
        FilterRegistrationBean<CorrelacaoFilter> registro = new FilterRegistrationBean<>(new CorrelacaoFilter());
        registro.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 1);
        return registro;
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
