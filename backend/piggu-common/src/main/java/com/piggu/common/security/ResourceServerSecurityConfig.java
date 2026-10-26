package com.piggu.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Seguranca padrao dos servicos de dominio: sem sessao, sem CSRF (API pura),
 * tudo autenticado por JWT exceto o actuator de saude e o de metricas.
 *
 * <p>O {@code /actuator/prometheus} fica aberto porque quem le e o Prometheus, pela rede
 * interna do Compose: de fora, o Caddy so repassa {@code /api} e o gateway nao roteia
 * {@code /actuator}. As metricas trazem rota, status e tempos, nunca dado de pessoa.</p>
 *
 * <p>Cada servico importa esta classe e adiciona apenas as suas excecoes.</p>
 */
@Configuration
@EnableMethodSecurity
public class ResourceServerSecurityConfig implements WebMvcConfigurer {

    /**
     * Contrato da API e Swagger UI (springdoc). So existem com {@code springdoc.api-docs.enabled},
     * ligado no perfil dev e nos testes; em producao ficam desligados e o gateway nao os roteia.
     */
    public static final String[] ROTAS_DO_CONTRATO = {
            "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**"};

    /** Recusa quem ainda nao informou o CPF; o identity desliga (e la que se informa). */
    @Value("${piggu.cpf.exigir:true}")
    private boolean exigirCpf;

    @Value("${springdoc.api-docs.enabled:false}")
    private boolean contratoLigado;

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> {
                    if (contratoLigado) {
                        rotas.requestMatchers(HttpMethod.GET, ROTAS_DO_CONTRATO).permitAll();
                    }
                    rotas.requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                            .anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new PigguJwtAuthenticationConverter())))
                .build();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        if (exigirCpf) {
            registro.addInterceptor(new CpfObrigatorio());
        }
    }
}
