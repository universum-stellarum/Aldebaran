package br.api.universum.serverconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import br.api.universum.serverconfig.config.ClientRegistry;
import br.api.universum.serverconfig.config.ServerEnvironment;
import br.api.universum.serverconfig.repository.AuthorizedEnvironmentRepository;
import br.api.universum.serverconfig.repository.ConfigurationFiles;
import br.api.universum.serverconfig.secrets.SecretResolver;
import br.api.universum.serverconfig.security.RequestPolicy;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RepositoryAuthorizationTests {
    @TempDir Path directory;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniesBeforeAnyFileReadOrSecretResolution() throws Exception {
        var registration = new ClientRegistry.Client("auth-service", "auth-client",
                new ClientRegistry.Password(IntegrationFixture.HASH));
        var registry = new ClientRegistry(List.of(registration));
        var policy = new RequestPolicy(new ServerEnvironment("dev"), registry);
        var lookups = new AtomicInteger();
        var resolver = new SecretResolver(name -> { lookups.incrementAndGet(); return "never"; });
        var repository = new AuthorizedEnvironmentRepository((application, profile, label) -> {
            throw new AssertionError("Native must not be invoked before authorization");
        }, policy, registry, resolver, new ConfigurationFiles(directory));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("another-client", "redacted", List.of()));
        assertThatThrownBy(() -> repository.findOne("auth-service", "dev", null))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(lookups).hasValue(0);
    }

    @Test
    void rejectsLabelsAndProfilesEvenWhenInvokedWithoutHttp() throws Exception {
        var registration = new ClientRegistry.Client("auth-service", "auth-client",
                new ClientRegistry.Password(IntegrationFixture.HASH));
        var registry = new ClientRegistry(List.of(registration));
        var policy = new RequestPolicy(new ServerEnvironment("dev"), registry);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("auth-client", "redacted", List.of()));
        var repository = new AuthorizedEnvironmentRepository((application, profile, label) -> {
            throw new AssertionError("Native must not be invoked for rejected arguments");
        }, policy, registry, new SecretResolver(name -> "never"), new ConfigurationFiles(directory));
        assertThatThrownBy(() -> repository.findOne("auth-service", "prod", null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> repository.findOne("auth-service", "dev,prod", null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> repository.findOne("auth-service", "dev", "main"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
