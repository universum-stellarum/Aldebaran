# Verificações executadas

Execução local em 14/11/2026. Nenhuma credencial real foi usada. Os testes Docker geram
senhas, hashes e segredos descartáveis e removem seus containers e redes.

| Verificação | Resultado observado |
| --- | --- |
| Java local | Oracle JDK 21.0.12 |
| Maven Wrapper `mvnw.cmd -q verify` no Windows | 134 casos: 133 aprovados, 1 ignorado por ser específico de Linux; zero falhas/erros |
| `docker build -t universum-config-server:verification .` | BUILD SUCCESS |
| Maven `verify` dentro do estágio Docker Linux | 134 aprovados; zero falhas, erros ou skips |
| `python scripts/docker-smoke.py universum-config-server:verification` | Concluído com sucesso |
| `docker compose config --format json` em ambientes descartáveis | Validado sem impressão de credenciais |
| Compilação Java 21 de `scripts/HashPassword.java` | Aprovada |
| `python -m py_compile scripts/docker-smoke.py` | Aprovada |
| `git diff --check` | Sem erros de whitespace |

## Cobertura Maven

- Inicialização DEV/PROD; rejeição de ambiente ausente, inválido ou com espaços.
- Cadastro obrigatório, completo, nomes seguros, hashes bcrypt e usuários únicos sem distinguir capitalização.
- HTTP Basic real: credenciais válidas, ausentes e incorretas.
- Autorização por aplicação, acesso cruzado e isolamento dos perfis.
- Perfis combinados, labels, métodos de escrita, formatos alternativos, queries e caminhos codificados/traversal.
- Endpoint oficial Environment em JSON normal e na representação V2.
- Resolução de segredos, tipos YAML, precedência de configurações globais/específicas.
- Placeholder obrigatório ausente, namespace de outro serviço e variáveis internas.
- Ausência de segredos nos erros e logs capturados.
- Imports, metadados de ativação de perfil, múltiplos documentos YAML, variantes de arquivos e symlinks.
- Rejeição pelo repositório antes de qualquer acesso a arquivos ou resolução de segredos.
- Aplicação cadastrada sem arquivo retornando 404, sem fallback para somente o global.
- Health check público com status mínimo.
- Spring Cloud Config Client real com import obrigatório, perfil explícito e falha com credenciais erradas.
- Comportamento real do Native 5.0.6 mantendo placeholders antes da decoração.

O teste de symlink é executado somente no Linux para não exigir privilégios de criação
de links no Windows. Foi aprovado dentro do build Docker.

## Testes Docker e Compose

As duas instâncias foram iniciadas pelo **Compose real**, com a mesma imagem, ambientes distintos,
redes descartáveis e portas aleatórias publicadas somente em 127.0.0.1 para as verificações.

Resultados em DEV e PROD:

- HTTP **200** na consulta da própria aplicação, com segredo resolvido do ambiente real do processo.
- HTTP **401** sem autenticação, com senha inválida ou com identidade PROD tentando autenticar no DEV.
- HTTP **403** para outra aplicação, outro perfil, perfis combinados, label, YAML, recursos e Actuator não permitido.
- HTTP **404** para aplicação cadastrada sem YAML próprio.
- Nenhum segredo financeiro na resposta do auth-service; nenhum segredo PROD na resposta DEV.
- Hashes bcrypt e segredos contendo cifrão preservados pelo `env_file.format: raw`.
- Usuário `10001:10001`, filesystem somente leitura e porta interna configurável.
- Health check executado dentro do container; ambos chegaram ao estado Docker **healthy**.
- Sem senha de bootstrap, hash ou segredos de banco nos logs dos containers.
- Inicialização recusada com ambiente inválido e cadastro de segurança ausente.
- Todos os containers e redes criados pelo teste foram removidos.

A saída de `docker compose config` escapa cifrões para `$$` ao serializar o modelo.
O teste verifica essa representação e, separadamente, o valor literal efetivamente recebido pelo Java.

## Evidências locais e reprodução

Os logs locais estão em `target/verify.log`, `target/docker-build.log`,
`target/docker-smoke.log` e os relatórios JUnit em `target/surefire-reports/`.
São artefatos ignorados pelo Git, regeneráveis pelos comandos documentados no README.

A imagem final validada foi criada com o nome local `universum-config-server:verification`.
Nenhuma imagem foi enviada a registry, nenhum deploy foi feito no Dokploy e nenhum domínio real foi configurado.

## Limites das verificações

O ambiente Dokploy real, DNS, certificados HTTPS e políticas do proxy/firewall ainda dependem da
implantação do operador. Os testes usam HTTP em loopback/rede Docker controlada.
Não foram testadas integrações de negócio com PostgreSQL, Redis, Kafka ou provedores externos,
pois o Config Server distribui suas propriedades e não se conecta a esses serviços.

O health check valida o processo, sem garantir a presença de todos os segredos de todas as aplicações.
Não há HA, refresh distribuído ou rotação automática. Cada instância deve receber exclusivamente
os valores do ambiente correto; um valor PROD inserido manualmente no namespace DEV não pode
ser identificado pelo código como pertencente a outro ambiente.
