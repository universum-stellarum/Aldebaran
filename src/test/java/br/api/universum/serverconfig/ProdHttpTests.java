package br.api.universum.serverconfig;
import org.springframework.test.context.TestPropertySource;
@TestPropertySource(properties = "APP_ENV=prod")
class ProdHttpTests extends HttpContract { }
