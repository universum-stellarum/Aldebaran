# Universum Config Server

Servidor de configuraÃ§Ã£o em Java 21, Spring Boot 4.1.2 e Spring Cloud 2025.1.4 (Config 5.0.6).
Usa o backend Native oficial, HTTP Basic e identidades individuais, sem banco de dados ou backend Git.
O mesmo cÃ³digo e a mesma imagem atendem duas implantaÃ§Ãµes independentes: `config-server-dev` e `config-server-prod`.

## Arquitetura e responsabilidades

```text
src/main/java/br/api/universum/serverconfig/
â”œâ”€â”€ ServerConfigApplication.java
â”œâ”€â”€ config/
â”‚   â”œâ”€â”€ ClientRegistry.java
â”‚   â”œâ”€â”€ ServerEnvironment.java
â”‚   â”œâ”€â”€ RuntimeConfiguration.java
â”‚   â”œâ”€â”€ SecurityConfiguration.java
â”‚   â””â”€â”€ NativeRepositoryConfiguration.java
â”œâ”€â”€ security/
â”‚   â””â”€â”€ RequestPolicy.java
â”œâ”€â”€ repository/
â”‚   â”œâ”€â”€ ConfigurationFiles.java
â”‚   â””â”€â”€ AuthorizedEnvironmentRepository.java
â””â”€â”€ secrets/
    â”œâ”€â”€ SecretResolver.java
    â””â”€â”€ DistributionException.java
src/main/resources/universum-server.yml
src/test/java/br/api/universum/serverconfig/  # contratos HTTP DEV/PROD, cliente real e testes unitÃ¡rios
config/
â”œâ”€â”€ application.yml
â”œâ”€â”€ auth-service.yml
â”œâ”€â”€ finance-service.yml
â””â”€â”€ notification-service.yml
docker/healthcheck.sh
scripts/
â”œâ”€â”€ HashPassword.java
â””â”€â”€ docker-smoke.py
docs/VERIFICACAO.md
Dockerfile
compose.yml
.env.example
.dockerignore
.gitignore
pom.xml
mvnw / mvnw.cmd
```

- **config:** composiÃ§Ã£o dos componentes, cadastro declarativo e validaÃ§Ã£o do ambiente.
- **security:** polÃ­tica de acesso que associa o usuÃ¡rio autenticado Ã  aplicaÃ§Ã£o permitida.
- **repository:** validaÃ§Ã£o dos arquivos e decoraÃ§Ã£o da resposta do Native.
- **secrets:** resoluÃ§Ã£o restrita de placeholders e exceÃ§Ãµes sem dados sensÃ­veis.

Fluxo: Spring Security autentica â†’ polÃ­tica autoriza a URI exata â†’ repositÃ³rio confirma identidade,
aplicaÃ§Ã£o e perfil â†’ valida YAMLs â†’ Native carrega propriedades â†’ resolvedor substitui apenas
variÃ¡veis autorizadas â†’ controller oficial serializa o Environment.

O repositÃ³rio principal Ã© um `CompositeEnvironmentRepository` oficial contendo somente o decorador
autorizado. Isso impede que o composite automÃ¡tico acrescente outra resposta Native sem filtragem.
NÃ£o hÃ¡ controller prÃ³prio de configuraÃ§Ã£o, interfaces artificiais ou camada de persistÃªncia.

DependÃªncias de produÃ§Ã£o: Spring Cloud Config Server, Spring Security e Actuator.
O Config Client Ã© dependÃªncia **de teste**, usado para validar o protocolo real.
As versÃµes permanecem gerenciadas pelo parent do Boot e pelo BOM Cloud; o verificador de compatibilidade estÃ¡ habilitado.

## Isolamento de ambiente e endpoints

`APP_ENV` Ã© obrigatÃ³rio e aceita exatamente `dev` ou `prod`.
AusÃªncia, valor vazio ou outro valor impedem a inicializaÃ§Ã£o. O perfil Spring `native` permanece
incluÃ­do e independe de `APP_ENV`; nÃ£o defina `SPRING_PROFILES_ACTIVE=dev` para escolher o backend.

A Ãºnica rota de configuraÃ§Ã£o permitida Ã©:

```http
GET /<aplicaÃ§Ã£o-autorizada>/<APP_ENV>
Accept: application/json
Authorization: Basic ...
```

DEV rejeita PROD e PROD rejeita DEV. NÃ£o hÃ¡ fallback entre instÃ¢ncias.
Todos os demais caminhos de configuraÃ§Ã£o sÃ£o negados: `default`, `native`, perfis combinados,
nomes compostos, labels, arquivos, recursos, YAML, Properties, JSON plano, parÃ¢metros de query,
barra final e caminhos alternativos. CodificaÃ§Ãµes e traversal sÃ£o rejeitados pelo firewall
Spring Security ou pela comparaÃ§Ã£o da URI original, com HTTP 400 ou 403.
MÃ©todos diferentes de GET sÃ£o negados. Headers encaminhados nÃ£o alteram a autorizaÃ§Ã£o.

Sem credenciais ou com credenciais invÃ¡lidas: **401**. Identidade vÃ¡lida acessando outra aplicaÃ§Ã£o,
outro ambiente ou rota proibida: **403**. AplicaÃ§Ã£o cadastrada sem arquivo prÃ³prio: **404**.
Erros de configuraÃ§Ã£o ou segredo obrigatÃ³rio indisponÃ­vel: **500**, sem detalhes sensÃ­veis.
Uma aplicaÃ§Ã£o nÃ£o cadastrada nÃ£o pode ser consultada nem por outro usuÃ¡rio vÃ¡lido.

## Backend Native e precedÃªncia

`config/` contÃ©m somente templates versionados; segredos reais chegam pelo ambiente do processo.
`universum-server.yml` configura o servidor. O nome de configuraÃ§Ã£o do Boot Ã© explicitamente
`universum-server`, para que o prÃ³prio Boot nÃ£o carregue o `config/application.yml` dos clientes.

A localizaÃ§Ã£o declarada Ã© `spring.cloud.config.server.native.search-locations=file:./config/`.
A composiÃ§Ã£o Java restringe a localizaÃ§Ã£o efetiva ao diretÃ³rio canÃ´nico de
`universum.config-directory` (padrÃ£o `./config`; imagem: `/app/config`).
NÃ£o hÃ¡ busca em classpath, Git ou diretÃ³rios de label; `add-label-locations=false`.
`accept-empty=false` estÃ¡ habilitado e o decorador exige um arquivo especÃ­fico existente,
mesmo quando o global sozinho produziria propriedades.

O contrato deste projeto permite **um documento YAML por arquivo**, extensÃ£o `.yml`,
sem imports de Config Data, ativaÃ§Ã£o de perfis, configuraÃ§Ãµes `spring.main.*` ou criptografia.
Arquivos alternativos `.yaml`, `.properties` e variantes de perfil relacionadas Ã  aplicaÃ§Ã£o
ou ao global sÃ£o rejeitados. Fontes retornadas pelo Native sÃ£o novamente conferidas.
Links simbÃ³licos nÃ£o sÃ£o aceitos. Labels e leitura de arquivos arbitrÃ¡rios sÃ£o inacessÃ­veis.

Propriedades especÃ­ficas da aplicaÃ§Ã£o prevalecem sobre as globais. A lista `propertySources`
do protocolo oficial estÃ¡ em ordem de prioridade, da maior para a menor.
O cliente Spring faz o binding normal, incluindo nÃºmeros, booleanos e listas.
Propriedades do bootstrap/ambiente local do cliente ainda seguem a precedÃªncia do prÃ³prio Spring Boot;
nÃ£o use variÃ¡veis locais para duplicar os segredos distribuÃ­dos.

`config/application.yml` deve conter somente valores pÃºblicos compartilhados.
Placeholders sÃ£o proibidos nesse arquivo. A revisÃ£o do repositÃ³rio tambÃ©m deve impedir
segredos literais no global ou em qualquer arquivo versionado.

## ResoluÃ§Ã£o de segredos no servidor

O teste `NativeBehaviorTests` verifica que o Native 5.0.6 mantÃ©m placeholders de ambiente
na resposta. O `SecretResolver` resolve os valores antes da serializaÃ§Ã£o oficial,
**depois da autenticaÃ§Ã£o e das duas verificaÃ§Ãµes de autorizaÃ§Ã£o**.

Cada serviÃ§o possui um namespace derivado do seu nome:

| AplicaÃ§Ã£o | VariÃ¡veis que pode receber |
| --- | --- |
| auth-service | `DIST_AUTH_SERVICE_*` |
| finance-service | `DIST_FINANCE_SERVICE_*` |
| notification-service | `DIST_NOTIFICATION_SERVICE_*` |

Exemplo versionÃ¡vel:

```yaml
spring:
  datasource:
    password: ${DIST_AUTH_SERVICE_DATABASE_PASSWORD}
```

A implementaÃ§Ã£o usa busca exata em `System.getenv`, nunca `Environment.getProperty`
para resolver segredos. `CONFIG_CLIENTS_*`, `APP_ENV`, `SERVER_PORT`, `PATH`, propriedades
da JVM e namespaces de outros serviÃ§os nÃ£o podem ser referenciados pelos templates.

A sintaxe aceita Ã© `${NOME_EXATO}`. Defaults, referÃªncias a outras propriedades, expansÃ£o
recursiva, expressÃµes aninhadas, valores vazios e `{cipher}` nÃ£o sÃ£o suportados.
O valor de um segredo nÃ£o pode conter `${`.
Valores sÃ£o inseridos literalmente; nÃ£o sÃ£o reavaliados como expressÃµes.
Tipos YAML existentes sÃ£o preservados. VariÃ¡veis de ambiente continuam strings,
convertidas pelo binding do cliente conforme o tipo de destino.

A ausÃªncia de uma variÃ¡vel exigida torna indisponÃ­vel a configuraÃ§Ã£o daquela aplicaÃ§Ã£o;
nÃ£o hÃ¡ resposta parcial ou fallback. O health check de processo pode continuar UP.
Cadastros e `APP_ENV` sÃ£o validados na inicializaÃ§Ã£o; segredos distribuÃ­dos sÃ£o validados
na consulta autorizada. Valide todos os serviÃ§os apÃ³s cada deploy.

## Cadastro de microserviÃ§os e senhas

Copie `.env.example` para um `.env` local ou use seu conteÃºdo no painel Environment do Dokploy.
Preencha os campos vazios e remova cadastros de serviÃ§os que nÃ£o serÃ£o usados.
Nunca versione o `.env`, nÃ£o use as credenciais de DEV em PROD e nÃ£o forneÃ§a variÃ¡veis PROD Ã  instÃ¢ncia DEV.

```dotenv
CONFIG_CLIENTS_3_NAME=inventory-service
CONFIG_CLIENTS_3_USERNAME=inventory-dev
CONFIG_CLIENTS_3_PASSWORD_HASH=
DIST_INVENTORY_SERVICE_DATABASE_PASSWORD=
```

Preencha o hash gerado e o segredo; crie `config/inventory-service.yml` com referÃªncias
somente a `DIST_INVENTORY_SERVICE_*`. Nenhuma alteraÃ§Ã£o Java Ã© necessÃ¡ria.
Use Ã­ndices contÃ­guos a partir de zero. Nome e usuÃ¡rio devem ser Ãºnicos.
Nomes usam letras minÃºsculas, nÃºmeros e hÃ­fens; `application` e o prefixo `application-` sÃ£o reservados.

A propriedade Java Ã© `config.clients[n].password.hash`, correspondente ao nome de ambiente
`CONFIG_CLIENTS_n_PASSWORD_HASH`. O cadastro aceita hashes `{bcrypt}$2a$...`,
`{bcrypt}$2b$...` ou `{bcrypt}$2y$...`, com custo entre 10 e 16.
NÃ£o aceita texto puro, `{noop}`, cadastros incompletos ou lista vazia.
A senha original pertence somente ao bootstrap do cliente.

Para gerar o hash sem senha no histÃ³rico do shell, use JDK 21:

```bash
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt -DincludeScope=runtime
java --class-path "$(cat target/runtime-classpath.txt)" scripts/HashPassword.java
```

PowerShell:

```powershell
.\mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=target/runtime-classpath.txt" "-DincludeScope=runtime"
$configClasspath = Get-Content target/runtime-classpath.txt -Raw
java --class-path $configClasspath scripts/HashPassword.java
```

O utilitÃ¡rio solicita a senha sem eco e imprime um hash bcrypt de custo 12.
Cole o hash completo, **sem aspas**, no painel Environment.
A aplicaÃ§Ã£o usa `DelegatingPasswordEncoder` e usuÃ¡rios somente em memÃ³ria.

## ConstruÃ§Ã£o, execuÃ§Ã£o e verificaÃ§Ãµes

```bash
./mvnw verify
docker build -t universum-config-server:1.0.0 .
```

O Dockerfile executa `mvn verify` no estÃ¡gio Maven e usa Temurin Java 21 JRE no runtime.
A imagem contÃ©m templates, nunca o `.env`. O processo roda com UID/GID 10001.
O Compose usa filesystem somente leitura, tmpfs limitado, capabilities removidas e
`no-new-privileges`, sem modo privilegiado nem Docker socket.

Depois de preparar o `.env`:

```bash
docker compose config --quiet
docker compose up -d
```

No Dokploy a rede externa `dokploy-network` jÃ¡ deve existir. Para um teste local do Compose,
crie uma rede com esse nome se necessÃ¡rio. A configuraÃ§Ã£o padrÃ£o nÃ£o publica portas no host.
Para execuÃ§Ã£o direta da aplicaÃ§Ã£o no IntelliJ, configure `APP_ENV`, todos os `CONFIG_CLIENTS_*`
e os `DIST_*` necessÃ¡rios nas variÃ¡veis de ambiente da execuÃ§Ã£o, com working directory na raiz.
O Spring Boot nÃ£o lÃª automaticamente um arquivo `.env`.

Teste Docker automatizado, apÃ³s build e testes locais:

```bash
./mvnw test dependency:build-classpath -Dmdep.outputFile=target/runtime-classpath.txt -DincludeScope=runtime
docker build -t universum-config-server:verification .
python scripts/docker-smoke.py universum-config-server:verification
```

O script usa somente Python padrÃ£o, Java e Docker. Cria identidades aleatÃ³rias e duas instÃ¢ncias,
publica portas aleatÃ³rias **somente em 127.0.0.1 durante o teste**, verifica respostas reais,
segredos do ambiente, isolamento DEV/PROD, saÃºde, usuÃ¡rio nÃ£o root e falhas de inicializaÃ§Ã£o.
TambÃ©m valida o Compose sem imprimir seus valores resolvidos e remove os containers criados.

Os testes Maven fazem requisiÃ§Ãµes HTTP reais a servidores em portas aleatÃ³rias, exercitam DEV
e PROD e iniciam um Spring Cloud Config Client real, com import obrigatÃ³rio.
Nenhum teste requer PostgreSQL, Redis ou Kafka.

## Deploy DEV e PROD no Dokploy

1. Crie dois recursos **Docker Compose**, chamados `config-server-dev` e `config-server-prod`,
   ligados ao mesmo repositÃ³rio e arquivo `compose.yml`. NÃ£o use o modo Docker Stack com este arquivo.
2. Use a mesma tag imutÃ¡vel/digest em `CONFIG_SERVER_IMAGE` nas duas implantaÃ§Ãµes.
   Construa uma vez e distribua pelo registry da organizaÃ§Ã£o; o Compose tambÃ©m permite build local.
   Para uma imagem jÃ¡ publicada, use a opÃ§Ã£o de pull/execuÃ§Ã£o sem rebuild no deploy.
3. No Environment de DEV, defina `APP_ENV=dev`, usuÃ¡rios e segredos DEV.
   Em PROD, defina `APP_ENV=prod` e valores independentes de produÃ§Ã£o.
4. Mantenha `SERVER_PORT=8888` ou ajuste explicitamente. O proxy deve apontar Ã  mesma porta interna.
5. O Dokploy grava o ambiente em `.env` ao lado do Compose. O `env_file` o encaminha ao container.
   `environment` encaminha explicitamente ambiente e porta. A mera existÃªncia do arquivo nÃ£o faz essa injeÃ§Ã£o.
6. Use Docker Compose **2.30 ou superior**, necessÃ¡rio para `env_file.format: raw`.
   Escreva valores sem aspas e em uma linha. O modo raw preserva cifrÃµes em hashes/segredos;
   nÃ£o os duplique como `$$`. NÃ£o use `docker compose config` sem `--quiet` em logs de deploy,
   pois a saÃ­da completa contÃ©m os segredos resolvidos.
7. Em Domains, selecione o serviÃ§o `config-server`, informe o domÃ­nio real da respectiva instÃ¢ncia,
   protocolo interno HTTP e porta configurada. Ative HTTPS, certificado vÃ¡lido e redirecionamento HTTPâ†’HTTPS.
   Crie antes o registro DNS apontando para o servidor. Redeploy apÃ³s alteraÃ§Ãµes de domÃ­nio.
8. Confira Preview Compose para confirmar rede e roteamento. Valide saÃºde e uma consulta de cada aplicaÃ§Ã£o
   com suas credenciais e confira as rejeiÃ§Ãµes cruzadas antes de liberar o uso.

Os domÃ­nios `https://config-dev.seudominio.com` e `https://config.seudominio.com` sÃ£o
apenas exemplos; devem ser substituÃ­dos por domÃ­nios reais do operador.
NÃ£o configure credenciais no domÃ­nio/URL. TLS termina no proxy, e proxyâ†’container usa HTTP
em rede privada controlada. A porta interna nÃ£o deve ser publicada diretamente.

## Cliente Spring Boot

Adicione `org.springframework.cloud:spring-cloud-starter-config` e o BOM Spring Cloud
oficialmente compatÃ­vel com a versÃ£o de Boot do microserviÃ§o.

```yaml
spring:
  application:
    name: auth-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE}
  config:
    import: "configserver:${CONFIG_SERVER_URL}"
  cloud:
    config:
      profile: ${SPRING_PROFILES_ACTIVE}
      username: ${CONFIG_USERNAME}
      password: ${CONFIG_PASSWORD}
      fail-fast: true
```

AlÃ©m do nome fixo da aplicaÃ§Ã£o, sÃ£o necessÃ¡rias quatro informaÃ§Ãµes:
**URL, perfil, usuÃ¡rio e senha**.
Nenhum segredo de banco, JWT, Redis ou Kafka precisa ser repetido no cliente.

A propriedade explÃ­cita `spring.cloud.config.profile` Ã© indispensÃ¡vel neste contrato:
o Config Data normalmente faz uma primeira consulta ao perfil `default`.
Esta configuraÃ§Ã£o fixa a consulta remota em `dev` ou `prod` desde a primeira fase.
O teste com cliente real verifica esse comportamento. NÃ£o defina label.
NÃ£o use `optional:configserver:`, lista de URLs com PROD como fallback ou perfil padrÃ£o silencioso.

| ExecuÃ§Ã£o | Perfil | URL configurada | Credenciais |
| --- | --- | --- | --- |
| IntelliJ local | dev | HTTPS real do Config Server DEV | identidade DEV do serviÃ§o |
| MicroserviÃ§o DEV no Dokploy | dev | HTTPS real do Config Server DEV | identidade DEV do serviÃ§o |
| MicroserviÃ§o PROD no Dokploy | prod | HTTPS real do Config Server PROD | identidade PROD do serviÃ§o |

No IntelliJ, configure as quatro variÃ¡veis na Run Configuration.
No Dokploy, injete-as explicitamente pelo `environment` ou `env_file` do Compose do microserviÃ§o.
Preserve a validaÃ§Ã£o de certificados TLS. NÃ£o ponha a senha na URL, cÃ³digo, parÃ¢metros de JVM ou logs.

## Testando acesso

Exemplos para Bash; substitua o domÃ­nio por um domÃ­nio configurado.
O curl solicita a senha de modo interativo, evitando a senha no histÃ³rico.

```bash
# Identidade auth-dev associada ao auth-service: esperado HTTP 200.
curl --fail --user auth-dev https://config-dev.seudominio.com/auth-service/dev

# Mesma identidade consultando finance-service: esperado HTTP 403.
curl --output /dev/null --write-out '%{http_code}\n' --user auth-dev \
  https://config-dev.seudominio.com/finance-service/dev

# AusÃªncia de credenciais: esperado HTTP 401.
curl --output /dev/null --write-out '%{http_code}\n' \
  https://config-dev.seudominio.com/auth-service/dev
```

A primeira resposta contÃ©m segredos: use terminal privado e nÃ£o copie a saÃ­da para logs ou tickets.

## OperaÃ§Ã£o, atualizaÃ§Ã£o e rotaÃ§Ã£o

- **YAML versionado:** altere o template, execute testes, construa uma nova imagem e redeploy nas duas instÃ¢ncias.
  Clientes jÃ¡ iniciados mantÃªm as propriedades em memÃ³ria; reinicie-os para aplicar mudanÃ§as.
- **Cadastro ou variÃ¡veis:** altere o ambiente no Dokploy e recrie o container por redeploy.
  Um simples `docker restart` nÃ£o aplica alteraÃ§Ãµes no ambiente definido para um novo container.
  O cadastro em memÃ³ria exige reinicializaÃ§Ã£o; nÃ£o hÃ¡ recarregamento dinÃ¢mico.
- **Volume opcional:** Ã© possÃ­vel montar um diretÃ³rio completo e confiÃ¡vel em `/app/config:ro`,
  contendo somente esses templates. NÃ£o monte `/`, diretÃ³rios de credenciais ou configuraÃ§Ãµes internas.
  Native relÃª arquivos em consultas; faÃ§a atualizaÃ§Ãµes atÃ´micas e redeploy para alteraÃ§Ãµes consistentes.
- **RotaÃ§Ã£o Basic:** gere uma senha nova por serviÃ§o, cadastre seu hash no ambiente correto, redeploy
  do servidor e atualize o bootstrap/reinicie os respectivos clientes. HÃ¡ somente uma credencial ativa
  por aplicaÃ§Ã£o, portanto planeje a janela de transiÃ§Ã£o.
- **RotaÃ§Ã£o de segredos distribuÃ­dos:** atualize banco/provedor e variÃ¡vel correspondente; redeploy
  do Config Server e reinicie os consumidores. Para JWT, coordene a troca e a invalidaÃ§Ã£o/expiraÃ§Ã£o
  de tokens com os serviÃ§os; este projeto nÃ£o gerencia um conjunto de chaves com sobreposiÃ§Ã£o.
- **Backup:** preserve repositÃ³rio privado e tags/digests das imagens. FaÃ§a backup criptografado das
  variÃ¡veis do Dokploy, credenciais de bootstrap e configuraÃ§Ã£o dos domÃ­nios separadamente do Git.
  Restrinja acesso e teste restauraÃ§Ã£o de cada ambiente sem misturar seus backups.

## SaÃºde, rede, logs e limitaÃ§Ãµes

`GET /actuator/health` Ã© a Ãºnica rota sem autenticaÃ§Ã£o e retorna somente status de processo.
O health check usa TCP local e HTTP GET via bash, sem credencial especial.
Ele nÃ£o consulta configuraÃ§Ãµes de clientes e nÃ£o atesta que todos os segredos externos estÃ£o disponÃ­veis.
Os demais endpoints Actuator e os endpoints de refresh, monitoramento, criptografia e recursos
sÃ£o inacessÃ­veis pela polÃ­tica de negaÃ§Ã£o padrÃ£o. NÃ£o hÃ¡ Spring Cloud Bus.

O servidor usa logs JSON e nÃ£o registra Authorization, corpos de requisiÃ§Ãµes/respostas ou valores
de configuraÃ§Ã£o. Erros de parsing/resoluÃ§Ã£o perdem a causa original para evitar vazamento de linhas YAML.
NÃ£o habilite TRACE/DEBUG de HTTP, logging de payloads, access logs com credenciais nem captura de corpos no proxy/APM.

`server.forward-headers-strategy=none` evita confiar em headers encaminhados arbitrariamente.
A aplicaÃ§Ã£o nÃ£o usa esses headers para decidir TLS ou autorizaÃ§Ã£o.
O proxy deve sobrescrever headers encaminhados, impor HTTPS externamente e preservar Authorization
apenas atÃ© este serviÃ§o. Restrinja as redes internas e o acesso ao daemon Docker/Dokploy.

Um domÃ­nio HTTPS pÃºblico aumenta a superfÃ­cie de ataque. Aplique firewall, VPN ou allowlist no proxy,
quando viÃ¡vel, alÃ©m de limites de requisiÃ§Ãµes e tentativas de autenticaÃ§Ã£o no proxy.
Basic Ã© obrigatÃ³rio nos dois ambientes; HTTPS sozinho nÃ£o substitui identidade individual.
A rede padrÃ£o do Dokploy Ã© compartilhada: isolamento adicional por rede/proxy Ã© recomendado
quando aplicaÃ§Ãµes nÃ£o confiÃ¡veis tambÃ©m usam o host.

NÃ£o existe alta disponibilidade automÃ¡tica. Uma instÃ¢ncia/host indisponÃ­vel impede novos clientes
de obter configuraÃ§Ã£o e o bootstrap obrigatÃ³rio falha. Clientes jÃ¡ iniciados continuam com seus
valores em memÃ³ria, conforme seu prÃ³prio comportamento. NÃ£o hÃ¡ failover automÃ¡tico para outro ambiente,
renovaÃ§Ã£o automÃ¡tica de segredos nem atualizaÃ§Ã£o remota dos clientes.
Para HA, use rÃ©plicas **do mesmo ambiente**, com imagem, templates e variÃ¡veis idÃªnticos e roteamento prÃ³prio.

O isolamento pressupÃµe que o operador injete somente segredos do ambiente correto.
Nenhum cÃ³digo consegue distinguir um valor PROD inserido equivocadamente no namespace DEV.
Administradores do Dokploy/host e processos com acesso ao ambiente do container sÃ£o confiÃ¡veis.
Os clientes autorizados recebem segredos em texto claro dentro do canal TLS e devem proteger sua memÃ³ria e observabilidade.

## ReferÃªncias oficiais

- [Compatibilidade Spring Cloud e Spring Boot](https://spring.io/projects/spring-cloud)
- [Cloud 2025.1 com suporte a Boot 4.1](https://spring.io/blog/2026/06/11/spring-cloud-2025-1-2-aka-oakwood-has-been-released/)
- [Backend Native](https://docs.spring.io/spring-cloud-config/reference/server/environment-repository/file-system-backend.html)
- [Config Client e Config Data](https://docs.spring.io/spring-cloud-config/reference/client.html)
- [Spring Security HTTP Basic](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/basic.html)
- [VariÃ¡veis de ambiente no Dokploy](https://docs.dokploy.com/docs/core/docker-compose)
- [DomÃ­nios do Compose no Dokploy](https://docs.dokploy.com/docs/core/docker-compose/domains)
- [env_file raw do Docker Compose](https://docs.docker.com/reference/compose-file/services/#format)
