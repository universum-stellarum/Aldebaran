package br.api.universum.serverconfig;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import br.api.universum.serverconfig.repository.ConfigurationFiles;
import br.api.universum.serverconfig.secrets.DistributionException;
import br.api.universum.serverconfig.secrets.SecretResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigurationFilesTests {
    @TempDir Path directory;
    private final SecretResolver resolver = new SecretResolver(IntegrationFixture.DISTRIBUTED::get);

    @ParameterizedTest
    @ValueSource(strings = {"application-prod.yml", "application.yaml", "application.properties",
            "auth-service-dev.yml", "auth-service-prod.yml", "auth-service-default.yml", "auth-service.properties"})
    void refusesProfileAndAlternativeFiles(String filename) throws Exception {
        Files.writeString(directory.resolve("auth-service.yml"), "allowed: true\n");
        Files.writeString(directory.resolve(filename), "secret: should-never-be-distributed\n");
        assertThatThrownBy(() -> new ConfigurationFiles(directory).verify("auth-service", resolver, "DIST_AUTH_SERVICE_"))
                .isInstanceOf(DistributionException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.config.import", "spring.profiles.active", "spring.profiles.include",
            "spring.main.sources", "spring.config.activate.on-profile", "SPRING_CONFIG_IMPORT"})
    void refusesConfigurationLoadingMetadata(String property) throws Exception {
        Files.writeString(directory.resolve("auth-service.yml"), property + ": forbidden\n");
        assertThatThrownBy(() -> new ConfigurationFiles(directory).verify("auth-service", resolver, "DIST_AUTH_SERVICE_"))
                .isInstanceOf(DistributionException.class);
    }

    @Test
    void refusesSecretPlaceholdersInSharedFile() throws Exception {
        Files.writeString(directory.resolve("auth-service.yml"), "allowed: true\n");
        Files.writeString(directory.resolve("application.yml"), "secret: ${DIST_AUTH_SERVICE_DATABASE_PASSWORD}\n");
        assertThatThrownBy(() -> new ConfigurationFiles(directory).verify("auth-service", resolver, "DIST_AUTH_SERVICE_"))
                .isInstanceOf(DistributionException.class);
    }

    @Test
    void refusesMultipleYamlDocuments() throws Exception {
        Files.writeString(directory.resolve("auth-service.yml"), "allowed: true\n---\nsecret: denied\n");
        assertThatThrownBy(() -> new ConfigurationFiles(directory).verify("auth-service", resolver, "DIST_AUTH_SERVICE_"))
                .isInstanceOf(DistributionException.class);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void refusesSymlinks() throws Exception {
        Path outside = Files.createTempFile(directory.getParent(), "outside-config-", ".yml");
        try {
            Files.writeString(outside, "secret: external\n");
            Files.createSymbolicLink(directory.resolve("auth-service.yml"), outside);
            assertThatThrownBy(() -> new ConfigurationFiles(directory).verify("auth-service", resolver, "DIST_AUTH_SERVICE_"))
                    .isInstanceOf(DistributionException.class);
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
