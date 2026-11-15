package br.api.universum.serverconfig.config;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Declarative in-memory identities. Nested password.hash maps to CONFIG_CLIENTS_n_PASSWORD_HASH. */
@ConfigurationProperties("config")
public record ClientRegistry(List<Client> clients) {
    private static final Pattern APPLICATION = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern BCRYPT = Pattern.compile("\\{bcrypt}\\$2[aby]\\$(?:1[0-6])\\$[./A-Za-z0-9]{53}");

    public ClientRegistry {
        if (clients == null || clients.isEmpty()) {
            throw new IllegalArgumentException("CONFIG_CLIENTS must contain complete client registrations");
        }
        clients = List.copyOf(clients);
        Set<String> names = new HashSet<>();
        Set<String> usernames = new HashSet<>();
        Set<String> prefixes = new HashSet<>();
        for (Client client : clients) {
            if (!isComplete(client) || !names.add(client.name()) || !usernames.add(client.username().toLowerCase(Locale.ROOT))
                    || !prefixes.add(client.variablePrefix())) {
                throw new IllegalArgumentException("Invalid or duplicate CONFIG_CLIENTS registration; bcrypt cost 10..16 required");
            }
        }
    }

    private static boolean isComplete(Client client) {
        return client != null && matches(APPLICATION, client.name())
                && !client.name().equals("application") && !client.name().startsWith("application-")
                && matches(USERNAME, client.username()) && matches(BCRYPT, client.passwordHash());
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    public Client forApplication(String application) {
        return clients.stream().filter(client -> client.name().equals(application)).findFirst().orElse(null);
    }

    public record Client(String name, String username, Password password) {
        public String variablePrefix() {
            return "DIST_" + name.toUpperCase(Locale.ROOT).replace('-', '_') + "_";
        }

        public String passwordHash() {
            return password == null ? null : password.hash();
        }

        @Override
        public String toString() {
            return "Client[redacted]";
        }
    }

    public record Password(String hash) {
        @Override
        public String toString() {
            return "Password[redacted]";
        }
    }
}
