package br.api.universum.serverconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import br.api.universum.serverconfig.config.ServerEnvironment;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(classes = {ServerConfigApplication.class, IntegrationFixture.Variables.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.name=universum-server", "spring.cloud.config.enabled=false"})
@ExtendWith(OutputCaptureExtension.class)
abstract class HttpContract {
    @LocalServerPort int port;
    @Autowired ServerEnvironment deployment;
    @Autowired org.springframework.core.env.Environment serverSpringEnvironment;
    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) throws Exception {
        IntegrationFixture.register(properties);
    }

    HttpResponse<String> get(String path, String application, String password) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json");
        if (application != null) {
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    (application + "-client:" + password).getBytes(StandardCharsets.UTF_8)));
        }
        return http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesAuthorizedSecretsAndKeepsNativeEnabled() throws Exception {
        var response = get("/auth-service/" + deployment.profile(), "auth-service", IntegrationFixture.PASSWORD);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(serverSpringEnvironment.getActiveProfiles()).contains("native");
        var environment = new JsonMapper().readValue(response.body(), Environment.class);
        assertThat(environment.getName()).isEqualTo("auth-service");
        assertThat(environment.getProfiles()).containsExactly(deployment.profile());
        var merged = new HashMap<String, Object>();
        environment.getPropertySources().reversed().forEach(source ->
                source.getSource().forEach((key, value) -> merged.put(key.toString(), value)));
        assertThat(merged).containsEntry("spring.datasource.password", IntegrationFixture.SECRET)
                .containsEntry("spring.datasource.url", "jdbc:postgresql://postgres-auth:5432/auth")
                .containsEntry("universum.organization", "Universum Digitalis")
                .containsEntry("universum.http.read-timeout", "30s")
                .containsEntry("universum.jwt.expiration-seconds", 900);
        assertThat(response.body()).doesNotContain("${", "CONFIG_CLIENTS", IntegrationFixture.HASH,
                "test-only-finance-secret", "never-distribute-this", "universum-server.yml");
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    }

    @Test
    void rejectsMissingAndInvalidCredentials() throws Exception {
        String path = "/auth-service/" + deployment.profile();
        assertThat(get(path, null, null).statusCode()).isEqualTo(401);
        assertThat(get(path, "auth-service", "wrong-password").statusCode()).isEqualTo(401);
    }

    @Test
    void authorizesEachApplicationIndividually() throws Exception {
        assertThat(get("/finance-service/" + deployment.profile(), "auth-service", IntegrationFixture.PASSWORD)
                .statusCode()).isEqualTo(403);
        var finance = get("/finance-service/" + deployment.profile(), "finance-service", IntegrationFixture.PASSWORD);
        assertThat(finance.statusCode()).isEqualTo(200);
        assertThat(finance.body()).contains("test-only-finance-secret")
                .doesNotContain(IntegrationFixture.SECRET, "test-only-jwt-secret");
    }

    @Test
    void rejectsOtherEnvironmentAndCombinedProfiles() throws Exception {
        for (String profile : new String[] {"default", "native", "qa",
                deployment.profile().equals("dev") ? "prod" : "dev", "dev,prod", "dev,default", "DEV"}) {
            assertThat(get("/auth-service/" + profile, "auth-service", IntegrationFixture.PASSWORD).statusCode())
                    .as(profile).isEqualTo(403);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/auth-service/dev/main", "/main/auth-service-dev.yml",
            "/auth-service-dev.yml", "/auth-service-dev.yaml", "/auth-service-dev.json",
            "/auth-service-dev.properties", "/auth-service/dev/secret.txt",
            "/auth-service,finance-service/dev", "/application/dev", "/actuator/env",
            "/actuator/configprops", "/actuator", "/encrypt", "/decrypt", "/monitor", "/error",
            "/auth-service/dev/", "/auth-service/dev?resolvePlaceholders=false",
            "/auth-service/%64ev", "/auth-service/dev%2Cprod", "/auth-service/dev;anything",
            "//auth-service/dev", "/auth-service/../finance-service/dev",
            "/auth-service/dev/%2e%2e/%2e%2e/etc/passwd", "/auth-service/dev/..%252fprod"
    })
    void rejectsAllAlternativePaths(String path) throws Exception {
        assertThat(get(path, "auth-service", IntegrationFixture.PASSWORD).statusCode()).as(path).isIn(400, 403);
    }

    @Test
    void officialV2RepresentationAlsoUsesAuthorizedResolvedValues() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth-service/" + deployment.profile()))
                .header("Accept", org.springframework.cloud.config.environment.EnvironmentMediaType.V2_JSON)
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        ("auth-service-client:" + IntegrationFixture.PASSWORD).getBytes(StandardCharsets.UTF_8)))
                .GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(IntegrationFixture.SECRET)
                .doesNotContain("${", IntegrationFixture.HASH, "test-only-finance-secret", "universum-server.yml");
    }

    @Test
    void rejectsWriteMethods() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth-service/" + deployment.profile()))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        ("auth-service-client:" + IntegrationFixture.PASSWORD).getBytes(StandardCharsets.UTF_8)))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void reportsMissingApplicationAs404EvenWithSharedConfiguration() throws Exception {
        assertThat(get("/absent-service/" + deployment.profile(), "absent-service", IntegrationFixture.PASSWORD)
                .statusCode()).isEqualTo(404);
        assertThat(get("/unregistered-service/" + deployment.profile(), "auth-service", IntegrationFixture.PASSWORD)
                .statusCode()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken-service", "internal-service", "cross-service", "import-service", "invalid-service"})
    void failsSafelyWithoutLeaks(String application, CapturedOutput output) throws Exception {
        var response = get("/" + application + "/" + deployment.profile(), application, IntegrationFixture.PASSWORD);
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).doesNotContain(IntegrationFixture.SECRET, IntegrationFixture.HASH,
                "never-distribute-this", "test-only-parser-secret", "DIST_", "CONFIG_CLIENTS", "/etc/passwd");
        assertThat(output.getAll()).doesNotContain(IntegrationFixture.SECRET, IntegrationFixture.HASH,
                "never-distribute-this", "test-only-parser-secret", "Authorization: Basic");
    }

    @Test
    void providesOnlyMinimalUnauthenticatedHealth() throws Exception {
        var response = get("/actuator/health", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(get("/actuator/health/readiness", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void realSpringCloudClientImportsMandatoryConfiguration() {
        var client = clientApplication();
        try (var context = client.run("--spring.cloud.config.enabled=true")) {
            assertThat(context.getEnvironment().getProperty("spring.datasource.password"))
                    .isEqualTo(IntegrationFixture.SECRET);
            assertThat(context.getEnvironment().getProperty("universum.organization")).isEqualTo("Universum Digitalis");
            assertThat(context.getEnvironment().getProperty("universum.http.read-timeout")).isEqualTo("30s");
        }
    }

    @Test
    void realClientFailsWithWrongCredentials() {
        var client = clientApplication();
        client.setDefaultProperties(clientProperties("wrong-password"));
        assertThatThrownBy(() -> client.run("--spring.cloud.config.enabled=true")).hasMessageContaining("Could not locate PropertySource");
    }

    private SpringApplication clientApplication() {
        var client = new SpringApplication(ClientApplication.class);
        client.setWebApplicationType(WebApplicationType.NONE);
        client.setDefaultProperties(clientProperties(IntegrationFixture.PASSWORD));
        return client;
    }

    private Map<String, Object> clientProperties(String password) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.config.name", "client-probe");
        properties.put("spring.cloud.config.enabled", "true");
        properties.put("spring.application.name", "auth-service");
        properties.put("spring.profiles.active", deployment.profile());
        properties.put("spring.cloud.config.profile", deployment.profile());
        properties.put("spring.config.import", "configserver:http://127.0.0.1:" + port);
        properties.put("spring.cloud.config.username", "auth-service-client");
        properties.put("spring.cloud.config.password", password);
        properties.put("spring.cloud.config.fail-fast", "true");
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "OFF");
        return properties;
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ClientApplication { }
}
