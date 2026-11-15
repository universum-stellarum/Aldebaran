package br.api.universum.configclient;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** Executado em outra JVM com apenas as três variáveis de bootstrap do cliente. */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
public class ConfigClientProbe {
    public static void main(String[] args) {
        var application = new SpringApplication(ConfigClientProbe.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        // O classpath de teste contém o application.yml do servidor; um cliente real tem seu próprio arquivo.
        application.setDefaultProperties(Map.of("spring.config.name", "client-probe"));
        try (var context = application.run(args)) {
            var environment = context.getEnvironment();
            String name = environment.getProperty("spring.application.name");
            String profile = environment.getProperty("spring.profiles.active");
            String expected = "fictitious-" + name + "-" + profile + "-password";
            if (!expected.equals(environment.getProperty("spring.datasource.password"))) {
                throw new IllegalStateException("O cliente não recebeu o segredo descriptografado");
            }
            if (!"Universum Digitalis".equals(environment.getProperty("universum.organization"))) {
                throw new IllegalStateException("O cliente não recebeu a configuração comum");
            }
            System.out.println("CONFIG_CLIENT_OK application=" + name + " profile=" + profile);
        }
    }
}
