package br.api.universum.serverconfig.repository;

import br.api.universum.serverconfig.secrets.DistributionException;
import br.api.universum.serverconfig.secrets.SecretResolver;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.config.server.environment.EnvironmentNotFoundException;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.io.FileSystemResource;

/** Restricts Native to validated YAML documents without imports, symlinks or profile variants. */
public final class ConfigurationFiles {
    private final Path directory;

    public ConfigurationFiles(Path directory) throws IOException {
        this.directory = directory.toRealPath();
        if (!Files.isDirectory(this.directory)) {
            throw new IOException("Configuration directory is unavailable");
        }
    }

    public String[] searchLocations() {
        // Native expects directories and compares file paths rather than URI-encoded paths.
        return new String[] { "file:" + directory.toString().replace(File.separatorChar, '/') + "/" };
    }

    public boolean isAuthorizedSource(String source, String application) {
        return source.endsWith("/application.yml") || source.endsWith("/" + application + ".yml");
    }

    public void verify(String application, SecretResolver resolver, String variablePrefix) {
        rejectAlternativeFiles(application);
        Path service = directory.resolve(application + ".yml");
        if (!Files.isRegularFile(service)) {
            throw new EnvironmentNotFoundException("Configuration not found");
        }
        verifyFile(service, resolver, variablePrefix, false);
        Path shared = directory.resolve("application.yml");
        if (Files.exists(shared)) {
            verifyFile(shared, resolver, variablePrefix, true);
        }
    }

    private void rejectAlternativeFiles(String application) {
        for (String base : new String[] { "application", application }) {
            for (String extension : new String[] { ".yml", ".yaml", ".properties" }) {
                for (String suffix : new String[] { "", "-dev", "-prod", "-default", "-native" }) {
                    if (suffix.isEmpty() && extension.equals(".yml")) {
                        continue;
                    }
                    if (Files.exists(directory.resolve(base + suffix + extension))) {
                        throw new DistributionException();
                    }
                }
            }
        }
    }

    private void verifyFile(Path file, SecretResolver resolver, String variablePrefix, boolean shared) {
        try {
            if (Files.isSymbolicLink(file) || !file.toRealPath().getParent().equals(directory)) {
                throw new DistributionException();
            }
            var sources = new YamlPropertySourceLoader().load("validation", new FileSystemResource(file));
            if (sources.size() != 1) {
                throw new DistributionException();
            }
            var source = (EnumerablePropertySource<?>) sources.getFirst();
            for (String key : source.getPropertyNames()) {
                String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
                if (normalized.startsWith("springconfig") || normalized.startsWith("springprofiles")
                        || normalized.startsWith("springmain") || normalized.startsWith("encrypt")) {
                    throw new DistributionException();
                }
                resolver.resolve(source.getProperty(key), variablePrefix, shared);
            }
        } catch (Exception ignored) {
            // YAML parsing errors can contain whole source lines and secrets.
            throw new DistributionException();
        }
    }
}
