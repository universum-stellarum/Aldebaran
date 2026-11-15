package br.api.universum.serverconfig;

import static org.assertj.core.api.Assertions.assertThat;
import br.api.universum.serverconfig.repository.ConfigurationFiles;
import br.api.universum.serverconfig.secrets.SecretResolver;
import io.micrometer.observation.ObservationRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.core.env.StandardEnvironment;

class NativeBehaviorTests {
    @Test
    void confirmsOfficialNativeLeavesEnvironmentPlaceholdersUnresolved() throws Exception {
        var files = new ConfigurationFiles(Path.of("config"));
        files.verify("auth-service", new SecretResolver(IntegrationFixture.DISTRIBUTED::get), "DIST_AUTH_SERVICE_");
        var properties = new NativeEnvironmentProperties();
        properties.setSearchLocations(files.searchLocations());
        properties.setAddLabelLocations(false);
        var nativeRepository = new NativeEnvironmentRepository(new StandardEnvironment(), properties, ObservationRegistry.NOOP);
        var result = nativeRepository.findOne("auth-service", "dev", null);
        assertThat(result.getPropertySources()).isNotEmpty();
        assertThat(result.getPropertySources().stream().flatMap(source -> source.getSource().values().stream()).map(Object::toString))
                .contains("${DIST_AUTH_SERVICE_DATABASE_PASSWORD}");
    }
}
