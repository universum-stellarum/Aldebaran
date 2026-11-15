import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Java 21 source launcher: password is read from the terminal without echo or command-line arguments. */
class HashPassword {
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) {
            throw new IllegalStateException("Execute em um terminal interativo com Java 21");
        }
        char[] password = console.readPassword("Senha do cliente: ");
        try {
            System.out.println("{bcrypt}" + new BCryptPasswordEncoder(12).encode(new String(password)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
