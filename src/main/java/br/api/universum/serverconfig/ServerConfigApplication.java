package br.api.universum.serverconfig;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ServerConfigApplication {
    public static void main(String[] args) {
        var application = new SpringApplication(ServerConfigApplication.class);
        // Boot must never read the distributed config/application.yml as its own configuration.
        application.setDefaultProperties(Map.of("spring.config.name", "universum-server"));
        application.setAdditionalProfiles("native");
        application.run(args);
    }
}
