package com.piggu.identity.config;

import com.piggu.common.security.PigguJwtAuthenticationConverter;
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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(HttpMethod.POST, "/api/auth/google", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        // A Stripe nao manda token: o aviso e conferido pela assinatura HMAC.
                        .requestMatchers(HttpMethod.POST, "/api/billing/webhooks/stripe").permitAll()
                        // Idem para o RevenueCat: a prova e o segredo no Authorization.
                        .requestMatchers(HttpMethod.POST, "/api/billing/stores/*/purchases").permitAll()
                        .requestMatchers(HttpMethod.GET, "/.well-known/jwks.json").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(tokenForaDosAvisosDasLojas())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new PigguJwtAuthenticationConverter())))
                .build();
    }

    /**
     * O RevenueCat manda o segredo dele como "Authorization: Bearer ...". Nessa rota o
     * cabecalho nao e um JWT do Piggu: sem isto, o resource server tentaria valida-lo e
     * recusaria o aviso com 401 antes de chegar ao controller.
     */
    private static BearerTokenResolver tokenForaDosAvisosDasLojas() {
        DefaultBearerTokenResolver padrao = new DefaultBearerTokenResolver();
        return pedido -> pedido.getRequestURI().startsWith("/api/billing/stores/") ? null : padrao.resolve(pedido);
    }
}
