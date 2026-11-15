package br.api.universum.serverconfig.security;

import br.api.universum.serverconfig.config.ClientRegistry;
import br.api.universum.serverconfig.config.ServerEnvironment;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

/** Allows one canonical Config route and one minimal health route. Everything else is denied. */
public final class RequestPolicy {
    private final ServerEnvironment environment;
    private final ClientRegistry registry;

    public RequestPolicy(ServerEnvironment environment, ClientRegistry registry) {
        this.environment = environment;
        this.registry = registry;
    }

    public boolean isHealthCheck(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) && "/actuator/health".equals(request.getRequestURI())
                && request.getContextPath().isEmpty() && request.getQueryString() == null;
    }

    public boolean permits(HttpServletRequest request, Authentication authentication) {
        if (!"GET".equals(request.getMethod()) || request.getQueryString() != null
                || !request.getContextPath().isEmpty()) {
            return false;
        }
        return registry.clients().stream().anyMatch(client -> client.username().equals(authentication.getName())
                && request.getRequestURI().equals("/" + client.name() + "/" + environment.profile()));
    }

    public boolean permits(String application, String profile, String label, Authentication authentication) {
        var client = registry.forApplication(application);
        return authentication != null && authentication.isAuthenticated() && client != null
                && client.username().equals(authentication.getName())
                && environment.profile().equals(profile) && label == null;
    }
}
