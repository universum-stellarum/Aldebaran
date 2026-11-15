package br.api.universum.serverconfig;
import org.springframework.test.context.TestPropertySource;
@TestPropertySource(properties = "APP_ENV=dev")
class DevHttpTests extends HttpContract { }
