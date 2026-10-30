package com.piggu.identity.config;

import com.piggu.common.security.PigguJwtAuthenticationConverter;
import com.piggu.common.security.ResourceServerSecurityConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * O identity e' o unico servico com rotas abertas: e' por elas que alguem ainda
 * sem token consegue obter o primeiro. Todo o resto segue autenticado.
 */
@Configuration
public class IdentitySecurityConfig {

    /** Contrato da API e Swagger UI: so no perfil dev e nos testes (ver ResourceServerSecurityConfig). */
    @Value("${springdoc.api-docs.enabled:false}")
    private boolean contratoLigado;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> {
                    if (contratoLigado) {
                        rotas.requestMatchers(HttpMethod.GET, ResourceServerSecurityConfig.ROTAS_DO_CONTRATO).permitAll();
                    }
                    rotas
                            .requestMatchers(HttpMethod.POST, "/api/auth/google", "/api/auth/apple", "/api/auth/cadastro", "/api/auth/entrar", "/api/auth/confirmar-email",
                                    "/api/auth/senha/esqueci", "/api/auth/senha/redefinir", "/api/auth/refresh", "/api/auth/logout",
                                    // Login com Google no app instalado: a volta do Google e a troca do codigo.
                                    "/api/auth/google/app", "/api/auth/google/app/troca").permitAll()
                            // A Stripe nao manda token: o aviso e conferido pela assinatura HMAC.
                            .requestMatchers(HttpMethod.POST, "/api/billing/webhooks/stripe").permitAll()
                            // A App Store tambem nao: o corpo e um JWS assinado pela Apple.
                            .requestMatchers(HttpMethod.POST, "/api/billing/webhooks/app-store").permitAll()
                            // O Google Play manda um token OIDC do Google, conferido no servico (GooglePlayLoja).
                            .requestMatchers(HttpMethod.POST, "/api/billing/webhooks/google-play").permitAll()
                            .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                            // Erro de JavaScript do app: chega sem token, ate da tela de login (ErrosDoAppController).
                            .requestMatchers(HttpMethod.POST, "/api/app/erros").permitAll()
                            // Metricas: so a rede interna alcanca (ver ResourceServerSecurityConfig).
                            .requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                            .anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(tokenForaDosAvisosDasLojas())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new PigguJwtAuthenticationConverter())))
                .build();
    }

    /**
     * O push do Google Play manda um token OIDC do Google. Nessa rota o cabecalho nao e um JWT do Piggu: sem isto, o resource server tentaria valida-lo e
     * recusaria o aviso com 401 antes de chegar ao controller.
     */
    private static BearerTokenResolver tokenForaDosAvisosDasLojas() {
        DefaultBearerTokenResolver padrao = new DefaultBearerTokenResolver();
        return pedido -> pedido.getRequestURI().equals("/api/billing/webhooks/google-play") ? null : padrao.resolve(pedido);
    }
}
