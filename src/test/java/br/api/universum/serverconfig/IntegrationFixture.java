package br.api.universum.serverconfig;

import br.api.universum.serverconfig.secrets.SecretResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;

final class IntegrationFixture {
    static final String PASSWORD = "test-only-bootstrap-password";
    static final String HASH = "{bcrypt}" + new BCryptPasswordEncoder(10).encode(PASSWORD);
    static final String SECRET = "test-only-database-secret-9e350";
    static final Map<String, String> DISTRIBUTED = Map.ofEntries(
            Map.entry("DIST_AUTH_SERVICE_DATABASE_HOST", "postgres-auth"),
            Map.entry("DIST_AUTH_SERVICE_DATABASE_PORT", "5432"),
            Map.entry("DIST_AUTH_SERVICE_DATABASE_NAME", "auth"),
            Map.entry("DIST_AUTH_SERVICE_DATABASE_USERNAME", "auth-db"),
            Map.entry("DIST_AUTH_SERVICE_DATABASE_PASSWORD", SECRET),
            Map.entry("DIST_AUTH_SERVICE_REDIS_HOST", "redis-auth"),
            Map.entry("DIST_AUTH_SERVICE_REDIS_PASSWORD", "test-only-redis-secret"),
            Map.entry("DIST_AUTH_SERVICE_JWT_SECRET", "test-only-jwt-secret"),
            Map.entry("DIST_AUTH_SERVICE_NOTIFICATION_URL", "https://notification.invalid"),
            Map.entry("DIST_FINANCE_SERVICE_DATABASE_HOST", "postgres-finance"),
            Map.entry("DIST_FINANCE_SERVICE_DATABASE_PORT", "5432"),
            Map.entry("DIST_FINANCE_SERVICE_DATABASE_NAME", "finance"),
            Map.entry("DIST_FINANCE_SERVICE_DATABASE_USERNAME", "finance-db"),
            Map.entry("DIST_FINANCE_SERVICE_DATABASE_PASSWORD", "test-only-finance-secret"),
            Map.entry("DIST_FINANCE_SERVICE_AUTH_URL", "https://auth.invalid"),
            Map.entry("CONFIG_CLIENTS_0_PASSWORD_HASH", HASH),
            Map.entry("INTERNAL_SENTINEL", "never-distribute-this"));

    static void register(DynamicPropertyRegistry properties) throws IOException {
        Path parent = Path.of("target").toAbsolutePath();
        Files.createDirectories(parent);
        Path directory = Files.createTempDirectory(parent, "config-test-");
        for (String file : new String[] {"application.yml", "auth-service.yml", "finance-service.yml"}) {
            Files.copy(Path.of("config", file), directory.resolve(file));
        }
        String auth = Files.readString(directory.resolve("auth-service.yml"))
                .replace("expiration-seconds: 900", "expiration-seconds: 900\n  http:\n    read-timeout: 30s");
        Files.writeString(directory.resolve("auth-service.yml"), auth);
        Files.writeString(directory.resolve("broken-service.yml"), "secret: ${DIST_BROKEN_SERVICE_MISSING}\n");
        Files.writeString(directory.resolve("internal-service.yml"), "secret: ${CONFIG_CLIENTS_0_PASSWORD_HASH}\n");
        Files.writeString(directory.resolve("cross-service.yml"), "secret: ${DIST_AUTH_SERVICE_DATABASE_PASSWORD}\n");
        Files.writeString(directory.resolve("import-service.yml"),
                "spring:\n  config:\n    import: file:/etc/passwd\n");
        Files.writeString(directory.resolve("invalid-service.yml"), "secret: [test-only-parser-secret\n");
        properties.add("universum.config-directory", directory::toString);
        int index = 0;
        for (String name : new String[] {"auth-service", "finance-service", "broken-service",
                "internal-service", "cross-service", "absent-service", "import-service", "invalid-service"}) {
            String prefix = "config.clients[" + index++ + "].";
            properties.add(prefix + "name", () -> name);
            properties.add(prefix + "username", () -> name + "-client");
            properties.add(prefix + "password.hash", () -> HASH);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Variables {
        @Bean @Primary
        SecretResolver testSecretResolver() {
            return new SecretResolver(DISTRIBUTED::get);
        }
    }
}
