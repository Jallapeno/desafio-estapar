# Desafio Estapar — estacionamento

Backend em Java 21 e Kotlin 2.1.21, com Micronaut, MySQL 8 e Flyway. Recebe os eventos do simulador em `POST /webhook` e consulta a receita em `GET /revenue`.

## Requisitos

- Para executar a API fora do Docker: JDK 21 disponível em `JAVA_HOME` e no `PATH`.
- Docker Engine ou Docker Desktop com containers Linux em execução.
- Portas 3003 (aplicação), 8080 (simulador no Compose) e 3306 (MySQL) disponíveis. No fluxo manual com rede host, o simulador usa a porta 3000.
- Gradle Wrapper incluído no repositório: não é necessário instalar Gradle globalmente.

A plataforma Micronaut está fixada em `4.7.6` no `gradle.properties`; os módulos resolvidos pelo BOM podem ter versões distintas. O plugin Gradle Micronaut é `4.5.3`.

## Execução completa com Docker Compose

Na raiz do projeto, copie o exemplo e defina `DB_PASSWORD` e `DB_ROOT_PASSWORD` no `.env`:

```powershell
Copy-Item .env.example .env
docker compose up -d --build --wait
```

O build compila a API em um estágio com Gradle 8.12.1/JDK 21 e executa o JAR em outro com JRE 21, como usuário sem privilégios. Não é necessário instalar Java ou Gradle no host. O contexto de build inclui apenas os fontes de produção e os arquivos necessários à compilação; `.env`, histórico Git e artefatos locais ficam fora da imagem.

O Compose sobe MySQL, simulador real `cfontes0estapar/garage-sim:1.0.0` e API na mesma rede. A API aguarda o healthcheck do MySQL e usa `http://simulator:3000` para sincronizar a garagem, com as retentativas de inicialização já existentes. O simulador recebe `CLIENT_WEBHOOK_URL=http://api:3003/webhook`. O healthcheck da API verifica o contrato OpenAPI após a inicialização. Este fluxo não utiliza `--network host` nem exige habilitar host networking no Docker Desktop.

- [Swagger UI](http://localhost:3003/swagger-ui/index.html)
- [Receita do setor A no dia atual UTC](http://localhost:3003/revenue?sector=A)
- Portas publicadas: `SERVER_PORT` (padrão 3003), `SIMULATOR_PORT` (8080) e `DB_PORT` (3306). Entre containers, as portas são fixas: API 3003, simulador 3000 e MySQL 3306; `DB_HOST` e `SIMULATOR_URL` do `.env` são usados apenas no fluxo com API local.

Para conferir os serviços e acompanhar os eventos:

```powershell
docker compose ps
docker compose logs --tail=100 api simulator
```

Para parar os três serviços preservando o volume do banco:

```powershell
docker compose down
```

O fluxo abaixo é a alternativa para executar a API diretamente na JVM. Use um fluxo por vez para evitar disputa pelas mesmas portas.

## Execução local no PowerShell

Na raiz do projeto:

```powershell
Copy-Item .env.example .env
```

Edite `DB_PASSWORD` e `DB_ROOT_PASSWORD` no `.env`. O Docker Compose lê esse arquivo automaticamente; a JVM iniciada pelo Gradle não o carrega. Importe as variáveis nesta mesma sessão:

```powershell
Get-Content .env | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line.Split('=', 2)
        Set-Item -Path "Env:$($parts[0])" -Value $parts[1]
    }
}
java -version
.\gradlew.bat --version
docker compose up -d --wait mysql
```

O exemplo `.env` usa valores simples, sem aspas ou interpolação; o trecho acima apenas os importa, sem executar seu conteúdo. Mantenha `SERVER_PORT=3003`, pois é a porta esperada pelo simulador. As variáveis do Compose inicializam o usuário e o banco somente quando o volume MySQL ainda está vazio.

Inicie o simulador fornecido pelo desafio antes da aplicação:

```powershell
docker run -d --name estapar-simulator --network host cfontes0estapar/garage-sim:1.0.0
$env:SIMULATOR_URL="http://localhost:3000"
.\gradlew.bat run
```

No Docker Desktop, a rede host exige versão 4.34 ou posterior e a opção **Settings → Resources → Network → Enable host networking**, com reinício do Docker Desktop. Isso permite ao container alcançar o webhook no host. Veja a [documentação oficial da rede host](https://docs.docker.com/engine/network/drivers/host/).

Nesta execução com rede host, a imagem real escuta em 3000; a variável acima substitui o valor do `.env`. A aplicação busca `GET http://localhost:3000/garage`, valida a configuração e persiste setores/vagas. Falhas transitórias recebem retentativas exponenciais. Se a sincronização inicial não terminar, a inicialização falha: confira os logs, corrija a dependência e execute novamente. Reiniciar preserva a ocupação das vagas no upsert. A configuração recebida é um snapshot completo: pode acrescentar setores/vagas e atualizar preços/capacidade, mas não remover identidades nem mudar setor/coordenadas de vagas existentes. Reduções abaixo da ocupação ativa são rejeitadas. Essas validações usam o lock dos eventos e antecedem qualquer escrita; uma configuração incompatível faz a sincronização falhar sem alterar dados ou histórico.

Em Linux/macOS, use `./gradlew` no lugar de `.\gradlew.bat`. Para importar o exemplo `.env` em um shell POSIX, use `set -a`, depois `. ./.env` e então `set +a`.

Para parar a infraestrutura local preservando os dados:

```powershell
docker stop estapar-simulator
docker compose down
```

## Configuração

| Variável | Padrão / finalidade |
|---|---|
| `SERVER_PORT` | `3003`, contrato do webhook |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | `localhost`, `3306`, `estapar_garage` |
| `DB_USER`, `DB_PASSWORD` | Obrigatórias para a aplicação |
| `DB_ROOT_PASSWORD` | Obrigatória para inicializar o MySQL no Compose |
| `SIMULATOR_PORT` | `8080`, porta publicada do simulador no Compose |
| `SIMULATOR_URL` | `http://localhost:8080` |
| `SIMULATOR_SYNC_ON_STARTUP` | `true`; desabilitada nos testes isolados |
| `SIMULATOR_RETRY_ATTEMPTS` | `5` |
| `SIMULATOR_RETRY_DELAY`, `SIMULATOR_RETRY_MULTIPLIER` | `1s`, `2.0` |

Não versione `.env`. Credenciais de testes são geridas pelo Testcontainers, em banco separado do Compose.

## API

### Swagger UI e OpenAPI

Com a aplicação em execução, abra [Swagger UI](http://localhost:3003/swagger-ui/index.html).
O contrato gerado está em [OpenAPI YAML](http://localhost:3003/swagger/estapar.yml).
A geração ocorre durante o build, via Micronaut OpenAPI/KSP; não é necessário manter um YAML manual.

A interface documenta os exemplos dos três eventos, campos condicionais e respostas HTTP. Para experimentar `GET /revenue` no navegador, use os query params: o body GET está documentado para outros clientes, mas navegadores não o enviam pelo Swagger UI. Os recursos da interface são servidos pela própria aplicação.

Configuração baseada no [guia oficial Micronaut OpenAPI](https://micronaut-projects.github.io/micronaut-openapi/latest/guide/).

### Eventos

```http
POST /webhook
Content-Type: application/json
```

```json
{"license_plate":"ABC1234","entry_time":"2025-01-01T10:00:00.000Z","event_type":"ENTRY"}
```

```json
{"license_plate":"ABC1234","lat":-23.561684,"lng":-46.655981,"event_type":"PARKED"}
```

```json
{"license_plate":"ABC1234","exit_time":"2025-01-01T11:15:00.000Z","event_type":"EXIT"}
```

Use no `PARKED` as coordenadas exatas recebidas do simulador; o exemplo acima não garante uma vaga na configuração efetiva.

Payloads válidos recebem HTTP 200, inclusive duplicatas e rejeições de negócio como garagem cheia, vaga ocupada ou coordenada desconhecida. Essas rejeições são registradas em log e não alteram os dados. JSON inválido, campos obrigatórios ausentes, coordenadas fora do intervalo/precisão e saída anterior à entrada ativa recebem HTTP 400. Falhas inesperadas de infraestrutura não são convertidas em sucesso.

A imagem real do simulador envia datas sem fuso (`2026-11-18T18:02:01`); somente nos campos `entry_time` e `exit_time` do webhook, esse formato é interpretado como UTC. Datas com `Z` ou offset explícito continuam aceitas, sem alteração no instante representado.

ENTRY e EXIT aceitam timestamps com precisão de milissegundos, compatível com `DATETIME(3)`; frações menores retornam HTTP 400 antes de qualquer alteração. Casas adicionais contendo apenas zeros são aceitas.

A placa é normalizada com trim e maiúsculas. Sessões anteriores encerradas são mantidas para permitir reentrada da mesma placa.

### Receita

```http
GET /revenue?sector=A&date=2025-01-01
```

Também aceita o corpo JSON especificado no desafio:

```http
GET /revenue
Content-Type: application/json

{"sector":"A","date":"2025-01-01"}
```

Exemplo no PowerShell, usando o cliente curl nativo:

```powershell
curl.exe 'http://localhost:3003/revenue?sector=A&date=2025-01-01'
```

```json
{"amount":18.00,"currency":"BRL","timestamp":"2025-01-03T12:00:00Z"}
```

`sector` é obrigatório. Sem `date`, utiliza o dia atual UTC. Setor/data sem receita retornam `0.00`; filtros conflitantes entre body e query ou data inválida retornam 400. O timestamp representa o instante usado como limite da consulta; saídas futuras são excluídas. O GET com body é lido pela API `ByteBody` no executor de bloqueio porque o binding padrão do Micronaut ignora esse corpo. Query params oferecem a alternativa para clientes que não enviam body em GET.

## Regras e premissas

- A capacidade global é a soma de `max_capacity` dos setores. `ENTERED` e `PARKED` consomem capacidade; apenas `PARKED` ocupa uma vaga física.
- A lotação anterior à entrada fixa o multiplicador: abaixo de 25% → `0.90`; até 50% → `1.00`; até 75% → `1.10`; acima de 75% → `1.25`. Com 100%, novas entradas são recusadas.
- A faixa usa contagens exatas. A razão gravada tem quatro casas, mas o arredondamento dessa razão não decide o preço.
- Até 30 minutos completos a cobrança é zero. Depois, cobra-se toda a permanência desde a entrada, incluindo a primeira hora, arredondando as horas para cima. O valor final usa `BigDecimal` e `HALF_UP`, sem arredondamento intermediário da tarifa.
- O preço-base é o valor atual do setor na saída; o multiplicador é o salvo na entrada.
- Coordenadas são `DECIMAL(10,6)`/`BigDecimal`, sem fallback por proximidade. Uma sessão estacionada não pode mudar de vaga por um segundo `PARKED`.
- Saída sem `PARKED` usa o primeiro setor por código, registra-o na sessão e aplica a mesma tolerância. É uma premissa do projeto: o PDF não define esse setor padrão.
- A receita pertence ao setor e ao dia UTC da saída, somando somente sessões `EXITED`.
- O ENTRY é deduplicado por placa e horário de entrada, inclusive após encerrar a visita; um horário novo permite nova visita. A verificação histórica ocorre sob o mesmo lock dos setores. O contrato não traz identificador único dos eventos nem timestamp no `PARKED`; para os demais eventos, a idempotência depende do estado da sessão e não garante deduplicação global entre visitas distintas. Um `EXIT` anterior à nova entrada é rejeitado.

## Arquitetura

```text
src/main/kotlin/br/com/estapar/parking/
  domain/           modelos, regras de tarifa e portas de persistência
  application/      casos de uso, serviços e fronteira transacional
  infrastructure/   HTTP, cliente do simulador, startup, JDBC e composição de beans
src/main/resources/db/migration/  V1: garagem; V2: sessões
src/test/                         domínio, integração e E2E HTTP
```

O domínio não depende do Micronaut. A aplicação recebe interfaces de repositório e `TransactionPort`; a infraestrutura implementa as portas e compõe os casos de uso. Os eventos passam pelo mesmo lock pessimista dos setores antes das leituras de sessão. Persistir a transição e alterar ocupação ocorre em uma única transação, protegendo concorrência e rollback.

Decisões e limites: [ADRs consolidados](docs/decisions/architecture.md) e [saída sem PARKED](docs/decisions/US-08-exit.md).

## Testes, build e cobertura

Com Docker em execução e JDK 21:

```powershell
.\gradlew.bat check
.\gradlew.bat assemble
```

`check` executa testes, relatório JaCoCo e gate de **85% de cobertura de linhas**. Os testes usam MySQL 8 via Testcontainers, porta HTTP aleatória e relógios fixos para receita. Nenhum teste usa o banco local do Compose. A sincronização com o simulador externo é desabilitada por padrão nos testes; os testes específicos de sincronização usam servidor HTTP controlado.

```powershell
.\gradlew.bat test --tests '*ParkingLifecycleE2ETest'
```

Relatórios gerados:

- `build/reports/tests/test/index.html`: resultados JUnit.
- `build/reports/jacoco/test/html/index.html`: cobertura por arquivo.
- `build/reports/jacoco/test/jacocoTestReport.xml`: cobertura processável.

O relatório exclui somente metadados gerados de Micronaut/KSP (`$Definition`, `$Introspection`, `$Intercepted`, `$Exec`). Modelos, serviços, adaptadores e controllers continuam no denominador. O JaCoCo está fixado em `0.8.13`, com filtros para bytecode Kotlin; consulte o [histórico oficial](https://www.jacoco.org/jacoco/trunk/doc/changes.html).

Se o Docker instalado rejeitar o cliente dos testes com `client version 1.32 is too old`, ajuste somente a sessão de execução para uma API suportada pelo daemon. Neste ambiente, foi validado:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Dapi.version=1.44'
.\gradlew.bat check
```

A [matriz CT-01 a CT-30](docs/testing/traceability.md) liga os critérios aos testes existentes e aos E2E adicionados na US-10. A execução HTTP usa dados sintéticos. Ela valida a aplicação e o MySQL; não constitui uma execução do simulador oficial, que deve ser verificado separadamente no ambiente local.

## Fluxo de entrega

Branches `codex/feature-us-XX-*` partem de `develop`; commits são pequenos e usam Conventional Commits. Cada US é revisada antes de merge `--no-ff` em `develop`. A publicação final em `main` é uma etapa de release posterior à revisão, não parte da execução automática dos testes.
