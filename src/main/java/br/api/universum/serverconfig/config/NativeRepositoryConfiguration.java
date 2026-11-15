package br.api.universum.serverconfig.config;

import br.api.universum.serverconfig.repository.AuthorizedEnvironmentRepository;
import br.api.universum.serverconfig.repository.ConfigurationFiles;
import br.api.universum.serverconfig.secrets.SecretResolver;
import br.api.universum.serverconfig.security.RequestPolicy;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.cloud.config.server.environment.CompositeEnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.ConfigurableEnvironment;

@Configuration(proxyBeanMethods = false)
public class NativeRepositoryConfiguration {
    @Bean
    ConfigurationFiles configurationFiles(ConfigurableEnvironment environment) throws IOException {
        return new ConfigurationFiles(Path.of(environment.getProperty("universum.config-directory", "./config")));
    }

    @Bean
    @Primary
    CompositeEnvironmentRepository environmentRepository(ConfigurableEnvironment environment, RequestPolicy policy,
            ClientRegistry registry, SecretResolver resolver, ConfigurationFiles files) {
        // Use only the canonical directory validated by ConfigurationFiles; never classpath or labels.
        var properties = new NativeEnvironmentProperties();
        properties.setSearchLocations(files.searchLocations());
        properties.setAddLabelLocations(false);
        var nativeRepository = new NativeEnvironmentRepository(environment, properties, ObservationRegistry.NOOP);
        var authorized = new AuthorizedEnvironmentRepository(nativeRepository, policy, registry, resolver, files);
        // Suppress the automatic composite: it would also include the unfiltered Native bean.
        return new CompositeEnvironmentRepository(new ArrayList<>(List.of(authorized)), ObservationRegistry.NOOP, true);
    }
}
