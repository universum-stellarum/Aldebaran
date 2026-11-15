package br.api.universum.serverconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import br.api.universum.serverconfig.secrets.DistributionException;
import br.api.universum.serverconfig.secrets.SecretResolver;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecretResolverTests {
    private final SecretResolver resolver = new SecretResolver(Map.of(
            "DIST_AUTH_SERVICE_PASSWORD", "literal-$\\-password", "CONFIG_CLIENTS_0_PASSWORD_HASH", "internal")::get);

    @Test
    void preservesNestedTypesAndUsesLiteralReplacement() {
        var value = Map.of("enabled", true, "count", 3, "nested",
                List.of("${DIST_AUTH_SERVICE_PASSWORD}", Map.of("timeout", 42)));
        assertThat(resolver.resolve(value, "DIST_AUTH_SERVICE_", false))
                .isEqualTo(Map.of("enabled", true, "count", 3, "nested",
                        List.of("literal-$\\-password", Map.of("timeout", 42))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"${CONFIG_CLIENTS_0_PASSWORD_HASH}", "${user.home}", "${PATH}",
            "${DIST_FINANCE_SERVICE_PASSWORD}", "${DIST_AUTH_SERVICE_MISSING}",
            "${DIST_AUTH_SERVICE_PASSWORD:fallback}", "${${DIST_AUTH_SERVICE_PASSWORD}}"})
    void rejectsUnapprovedOrUnresolvedReferences(String placeholder) {
        assertThatThrownBy(() -> resolver.resolve(placeholder, "DIST_AUTH_SERVICE_", false))
                .isInstanceOf(DistributionException.class).hasMessage("Configuration unavailable").hasNoCause();
    }

    @Test
    void globalFilesCannotResolveAnySecrets() {
        assertThatThrownBy(() -> resolver.resolve("${DIST_AUTH_SERVICE_PASSWORD}", "DIST_AUTH_SERVICE_", true))
                .isInstanceOf(DistributionException.class);
    }

    @Test
    void refusesIndirectExpansionAndBlankSecrets() {
        for (String value : List.of("${INTERNAL}", " ", "{cipher}encrypted")) {
            var unsafe = new SecretResolver(name -> value);
            assertThatThrownBy(() -> unsafe.resolve("${DIST_AUTH_SERVICE_PASSWORD}", "DIST_AUTH_SERVICE_", false))
                    .isInstanceOf(DistributionException.class);
        }
    }

    @Test
    void doesNotEvenLookUpUnauthorizedVariables() {
        var calls = new AtomicInteger();
        var counting = new SecretResolver(name -> { calls.incrementAndGet(); return "internal"; });
        assertThatThrownBy(() -> counting.resolve("${CONFIG_CLIENTS_0_PASSWORD_HASH}", "DIST_AUTH_SERVICE_", false))
                .isInstanceOf(DistributionException.class);
        assertThat(calls).hasValue(0);
    }
}
