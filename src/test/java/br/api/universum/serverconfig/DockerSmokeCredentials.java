package br.api.universum.serverconfig;

import java.util.UUID;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Called only by the Docker smoke-test script, never packaged in the application. */
public final class DockerSmokeCredentials {
    public static void main(String[] args) {
        String password = UUID.randomUUID().toString();
        System.out.println(password);
        System.out.println("{bcrypt}" + new BCryptPasswordEncoder(10).encode(password));
    }
}
