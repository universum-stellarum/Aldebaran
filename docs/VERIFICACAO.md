# Verificação da refatoração do Aldebaran

Resultados executados em 15/11/2026. Todos os valores usados nos testes são fictícios.

## Resultados

| Verificação | Resultado |
| --- | --- |
| `mvnw.cmd -q clean verify`, Java 21.0.12 no Windows | 24 testes aprovados; zero falhas, erros ou skips |
| `docker build -t aldebaran:verification .` | BUILD SUCCESS |
| `mvn verify` no estágio Linux do Dockerfile | 24 testes aprovados; zero falhas, erros ou skips |
| Execução real de uma instância pelo Compose | Aprovada |
| Compose sem `CONFIG_SERVER_BIND_IP` | Recusado, sem fallback para todas as interfaces |
| `git diff --check` | Sem erros de whitespace |

A suíte cobre o backend Git oficial com clone real por TCP loopback e branch main,
configurações comuns e específicas de Antares, Polaris e Finance em DEV/PROD,
precedência, perfil default, perfis múltiplos, labels, formatos YAML/Properties e atualização por fetch.

Os arquivos de teste recebem ciphertext produzido com a API oficial de criptografia.
As respostas HTTP contêm o valor descriptografado. Os endpoints administrativos normais
e suas variantes exigem HTTP Basic; o round-trip encrypt/decrypt foi verificado.

Um Config Client real é iniciado em outra JVM para cada perfil usando somente:
`SPRING_APPLICATION_NAME`, `SPRING_PROFILES_ACTIVE` e `SPRING_CONFIG_IMPORT`.
Foram aprovados o consumo de configurações/segredos e a falha de inicialização com
servidor indisponível, sem optional.

A fixture de Git e o pequeno probe de cliente existem somente em src/test.
Nenhum código de teste é empacotado como código da aplicação.

## Verificação do container

Foi iniciada uma única instância da imagem final pelo Compose, com:

- IP de publicação **127.0.0.1 somente para este teste local**, porta temporária e rede descartável.
- Um servidor Git temporário na rede isolada, sem porta publicada, contendo apenas os dados fictícios da suíte.
- Credenciais administrativas descartáveis e a chave de teste injetadas pelo ambiente real do processo.

Observado:

- Leitura sem Basic em `/antares/dev`, `/antares/prod` e `/antares/default`.
- Propriedades globais e específicas combinadas, com senhas descriptografadas.
- HTTP 401 em encrypt/decrypt sem credenciais; round-trip administrativo correto com senha contendo cifrão.
- Binding nativo de `ENCRYPT_KEY`, sem placeholder ou código Java de resolução.
- Clone/cache Git existente em `/tmp/aldebaran-config-git/.git`, dentro do tmpfs.
- Root filesystem somente leitura e usuário `10001:10001`.
- Ausência de /app/config e /app/examples na imagem final.
- Health check interno aprovado e estado Docker **healthy**.
- Chave e senha administrativa ausentes dos logs capturados.
- Containers e rede temporários removidos ao terminar.

## Alterações no repositório

Removidos os componentes ClientRegistry, ServerEnvironment, RuntimeConfiguration,
NativeRepositoryConfiguration, RequestPolicy, AuthorizedEnvironmentRepository,
ConfigurationFiles, SecretResolver e DistributionException.

Também removidos:

- `src/main/resources/universum-server.yml`.
- Os quatro YAMLs de distribuição antes presentes em `config/`.
- `scripts/HashPassword.java` e `scripts/docker-smoke.py`.
- Os dez arquivos dos testes anteriores: ConfigurationFilesTests, ConfigurationValidationTests,
  DevHttpTests, DockerSmokeCredentials, HttpContract, IntegrationFixture, NativeBehaviorTests,
  ProdHttpTests, RepositoryAuthorizationTests e SecretResolverTests.

Criados ou reformulados:

- Classe principal convencional e SecurityConfiguration mínima.
- `src/main/resources/application.yml` com Git, credenciais administrativas e Actuator.
- Sete YAMLs de exemplo em `examples/config-repo/`.
- ConfigServerIntegrationTests, LocalConfigRepository e ConfigClientProbe.
- POM, Dockerfile, compose.yml, .env.example, README e este relatório.

As dependências de produção continuam oficiais: Config Server, Security e Actuator.
Os testes usam starter-test e Config Client. Não há implementação própria de backend,
criptografia, resolução de placeholders ou cadastro de clientes.

## Evidências e limites

Logs locais ignorados pelo Git: `refactor-test.log`, `refactor-docker-build.log`
e `refactor-container.log`. Relatórios JUnit: `target/surefire-reports/`.
Os comandos Maven e Docker podem ser repetidos conforme o README.

Não foram usados GitHub privado, token real ou uma interface Tailscale real do servidor de implantação.
O transporte Git de teste foi TCP privado local, sem HTTPS/autenticação do GitHub.
Portanto não foram validados as permissões do token real, o TLS desse remoto, as ACLs da
tailnet ou o trajeto Docker → IP Tailscale no Linux de implantação. Esses passos estão no README.

Nenhum repositório remoto, domínio ou deploy Dokploy foi criado.
O limite de segurança da leitura é a infraestrutura privada: qualquer cliente que alcance
a API pode consultar outras aplicações e ambientes, como previsto para este laboratório.
