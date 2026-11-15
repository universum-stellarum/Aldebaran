package br.api.universum.serverconfig.repository;

import br.api.universum.serverconfig.config.ClientRegistry;
import br.api.universum.serverconfig.secrets.DistributionException;
import br.api.universum.serverconfig.secrets.SecretResolver;
import br.api.universum.serverconfig.security.RequestPolicy;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.config.server.environment.EnvironmentNotFoundException;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/** Authorizes identity first, validates files, then decorates the official Native response. */
public final class AuthorizedEnvironmentRepository implements EnvironmentRepository {
    private static final Logger LOG = LoggerFactory.getLogger(AuthorizedEnvironmentRepository.class);
    private final EnvironmentRepository delegate;
    private final RequestPolicy policy;
    private final ClientRegistry registry;
    private final SecretResolver resolver;
    private final ConfigurationFiles files;

    public AuthorizedEnvironmentRepository(EnvironmentRepository delegate, RequestPolicy policy,
            ClientRegistry registry, SecretResolver resolver, ConfigurationFiles files) {
        this.delegate = delegate;
        this.policy = policy;
        this.registry = registry;
        this.resolver = resolver;
        this.files = files;
    }

    @Override
    public Environment findOne(String application, String profile, String label) {
        return findOne(application, profile, label, false);
    }

    @Override
    public Environment findOne(String application, String profile, String label, boolean includeOrigin) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!policy.permits(application, profile, label, authentication)) {
            throw new AccessDeniedException("Forbidden");
        }
        String prefix = registry.forApplication(application).variablePrefix();
        try {
            files.verify(application, resolver, prefix);
            Environment source = delegate.findOne(application, profile, null, false);
            Environment result = new Environment(application, profile);
            for (PropertySource propertySource : source.getPropertySources()) {
                if (!files.isAuthorizedSource(propertySource.getName(), application)) {
                    throw new DistributionException();
                }
                boolean shared = propertySource.getName().endsWith("/application.yml");
                Object resolved = resolver.resolve(propertySource.getSource(), prefix, shared);
                result.add(new PropertySource(shared ? "shared" : application, (Map<?, ?>) resolved));
            }
            if (result.getPropertySources().isEmpty()) {
                throw new DistributionException();
            }
            return result;
        } catch (EnvironmentNotFoundException missing) {
            throw missing;
        } catch (Exception ignored) {
            LOG.warn("configuration_unavailable application={} profile={}", application, profile);
            throw new DistributionException();
        }
    }
}
