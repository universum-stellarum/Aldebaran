package br.api.universum.serverconfig.config;

import br.api.universum.serverconfig.security.RequestPolicy;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClientRegistry.class)
public class SecurityConfiguration {
    @Bean
    RequestPolicy requestPolicy(ServerEnvironment environment, ClientRegistry registry) {
        return new RequestPolicy(environment, registry);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(ClientRegistry registry) {
        var users = registry.clients().stream()
                .map(client -> User.withUsername(client.username())
                        .password(client.passwordHash()).authorities("CONFIG_CLIENT").build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, RequestPolicy policy) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, failure) -> {
                    response.setHeader("WWW-Authenticate", "Basic realm=\"universum-config\"");
                    response.setStatus(401);
                }))
                .exceptionHandling(errors -> errors
                        .accessDeniedHandler((request, response, failure) -> response.setStatus(403)))
                .authorizeHttpRequests(authorize -> authorize
                        // Internal servlet error dispatch only; an external GET /error is still denied.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(policy::isHealthCheck).permitAll()
                        .anyRequest().access((authentication, context) ->
                                new AuthorizationDecision(policy.permits(context.getRequest(), authentication.get()))))
                .build();
    }
}
