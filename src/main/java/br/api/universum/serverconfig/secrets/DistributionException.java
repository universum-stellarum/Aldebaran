package br.api.universum.serverconfig.secrets;

import org.springframework.cloud.config.server.environment.EnvironmentException;

/** Deliberately excludes original exceptions, property names and values. */
public final class DistributionException extends EnvironmentException {
    public DistributionException() {
        super("Configuration unavailable");
    }
}
