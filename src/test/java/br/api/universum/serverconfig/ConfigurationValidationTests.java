package br.api.universum.serverconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import br.api.universum.serverconfig.config.ClientRegistry;
import br.api.universum.serverconfig.config.RuntimeConfiguration;
import br.api.universum.serverconfig.config.ServerEnvironment;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ConfigurationValidationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Registration.class, RuntimeConfiguration.class);

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ClientRegistry.class)
    static class Registration { }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    void initializesBothEnvironments(String environment) {
        validRegistration(runner).withPropertyValues("APP_ENV=" + environment).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ServerEnvironment.class).profile()).isEqualTo(environment);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"qa", "DEV", "native", "dev,prod"})
    @NullAndEmptySource
    void rejectsInvalidOrMissingEnvironment(String value) {
        var configured = validRegistration(runner);
        if (value != null) configured = configured.withPropertyValues("APP_ENV=" + value);
        configured.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesEnvironmentWithWhitespace() {
        assertThatThrownBy(() -> new ServerEnvironment(" dev ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsWhenRegistrationIsMissing() {
        runner.withPropertyValues("APP_ENV=dev").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsForPartialRegistration() {
        runner.withPropertyValues("APP_ENV=dev", "config.clients[0].name=auth-service",
                "config.clients[0].username=auth-client").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsDuplicateNamesAndUsernames() {
        var first = client("auth-service", "auth-client", IntegrationFixture.HASH);
        assertThatThrownBy(() -> new ClientRegistry(List.of(first, first))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientRegistry(List.of(first,
                client("finance-service", "AUTH-CLIENT", IntegrationFixture.HASH))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "plaintext", "{noop}password", "{bcrypt}invalid",
            "{argon2}unsupported", "$2a$10$without-delegating-prefix"})
    void rejectsInvalidHashes(String hash) {
        assertThatThrownBy(() -> new ClientRegistry(List.of(client("auth-service", "auth-client", hash))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(hash.isEmpty() ? "plaintext" : hash);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application", "application-secret", "../prod", "auth,finance", "AUTH", "auth_service"})
    void rejectsUnsafeNames(String name) {
        assertThatThrownBy(() -> new ClientRegistry(List.of(client(name, "auth-client", IntegrationFixture.HASH))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ApplicationContextRunner validRegistration(ApplicationContextRunner target) {
        return target.withPropertyValues("config.clients[0].name=auth-service",
                "config.clients[0].username=auth-client", "config.clients[0].password.hash=" + IntegrationFixture.HASH);
    }

    private ClientRegistry.Client client(String name, String username, String hash) {
        return new ClientRegistry.Client(name, username, new ClientRegistry.Password(hash));
    }
}
