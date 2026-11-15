package br.api.universum.serverconfig.config;

/** Deployment environment is independent of the always-enabled native Spring profile. */
public record ServerEnvironment(String profile) {
    public ServerEnvironment {
        if (!"dev".equals(profile) && !"prod".equals(profile)) {
            throw new IllegalArgumentException("APP_ENV is required and must be dev or prod");
        }
    }
}
