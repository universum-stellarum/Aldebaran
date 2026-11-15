package br.api.universum.serverconfig.config;

import br.api.universum.serverconfig.secrets.SecretResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class RuntimeConfiguration {
    @Bean
    ServerEnvironment serverEnvironment(Environment environment) {
        return new ServerEnvironment(environment.getProperty("APP_ENV"));
    }

    @Bean
    SecretResolver secretResolver() {
        return new SecretResolver(System::getenv);
    }
}
