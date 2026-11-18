# Rastreabilidade de testes — CT-01 a CT-30

Os identificadores vêm do plano local de desenvolvimento. Esta cópia versionada liga cada cenário a uma suíte executável. Os testes de domínio usam casos dinâmicos nomeados por CT. Os E2E exercitam HTTP real até o MySQL, com configuração sintética de garagem e relógio fixo; a integração com o simulador usa um servidor controlado em sua suíte específica.

Todos os caminhos abaixo são relativos a `src/test/kotlin/br/com/estapar/parking/`.

| ID | Critério | Arquivo | Método / grupo |
|---|---|---|---|
| CT-01 | Tempo, tolerância e horas cobradas | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-01 to CT-08 should apply tolerance and charge complete stay rounded up` |
| CT-02 | Tempo, tolerância e horas cobradas | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-01 to CT-08 should apply tolerance and charge complete stay rounded up` |
| CT-03 | Tempo, tolerância e horas cobradas | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-01 to CT-08 should apply tolerance and charge complete stay rounded up` |
| CT-04 | Tempo, tolerância e horas cobradas | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-01 to CT-08 should apply tolerance and charge complete stay rounded up` |
| CT-05 | Tempo, tolerância e horas cobradas | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-01 to CT-08 should apply tolerance and charge complete stay rounded up` |
| CT-06 | Tempo, tolerância e horas cobradas | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-01 to CT-08 should apply tolerance and charge complete stay rounded up` |
| CT-07 | Tempo, tolerância e horas cobradas | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-01 to CT-08 should apply tolerance and charge complete stay rounded up` |
| CT-08 | Tempo, tolerância e horas cobradas | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-01 to CT-08 should apply tolerance and charge complete stay rounded up` |
| CT-09 | Faixas contínuas e valor cobrado | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-09 to CT-17 should apply existing entry multiplier to hourly price` |
| CT-10 | Faixas contínuas e valor cobrado | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-09 to CT-17 should apply existing entry multiplier to hourly price` |
| CT-11 | Faixas contínuas e valor cobrado | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-09 to CT-17 should apply existing entry multiplier to hourly price` |
| CT-12 | Faixas contínuas e valor cobrado | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-09 to CT-17 should apply existing entry multiplier to hourly price` |
| CT-13 | Faixas contínuas e valor cobrado | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-09 to CT-17 should apply existing entry multiplier to hourly price` |
| CT-14 | Faixas contínuas e valor cobrado | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-09 to CT-17 should apply existing entry multiplier to hourly price` |
| CT-15 | Faixas contínuas e valor cobrado | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-09 to CT-17 should apply existing entry multiplier to hourly price` |
| CT-16 | Faixas contínuas e valor cobrado | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-09 to CT-17 should apply existing entry multiplier to hourly price` |
| CT-17 | Faixas contínuas e valor cobrado | [PricingCalculatorTest](../../src/test/kotlin/br/com/estapar/parking/domain/service/PricingCalculatorTest.kt) | `CT-09 to CT-17 should apply existing entry multiplier to hourly price` |
| CT-18 | Reexecução idempotente da sincronização | [GarageSyncIntegrationTest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/simulator/GarageSyncIntegrationTest.kt) | `CT-18 should synchronize idempotently and preserve spot occupancy` |
| CT-19 | Recuperação na terceira tentativa HTTP | [GarageSyncIntegrationTest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/simulator/GarageSyncIntegrationTest.kt) | `CT-19 should retry transient simulator failures and succeed on third attempt` |
| CT-20 | Bloqueio em capacidade global máxima | [ParkingLifecycleE2ETest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/web/ParkingLifecycleE2ETest.kt) | `CT-20 CT-21 CT-22 should admit one of ten HTTP entrants then reopen after EXIT` |
| CT-21 | Saída libera capacidade e vaga para outra entrada | [ParkingLifecycleE2ETest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/web/ParkingLifecycleE2ETest.kt) | `CT-20 CT-21 CT-22 should admit one of ten HTTP entrants then reopen after EXIT` |
| CT-22 | Dez requisições HTTP disputam a última vaga | [ParkingLifecycleE2ETest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/web/ParkingLifecycleE2ETest.kt) | `CT-20 CT-21 CT-22 should admit one of ten HTTP entrants then reopen after EXIT` |
| CT-23 | ENTRY repetido não duplica sessão | [ParkingLifecycleE2ETest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/web/ParkingLifecycleE2ETest.kt) | `CT-23 CT-24 should keep webhook repeats idempotent and preserve charged revenue` |
| CT-24 | PARKED repetido preserva associação | [ParkingLifecycleE2ETest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/web/ParkingLifecycleE2ETest.kt) | `CT-23 CT-24 should keep webhook repeats idempotent and preserve charged revenue` |
| CT-25 | Duas visitas da mesma placa somadas na API | [ParkingLifecycleE2ETest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/web/ParkingLifecycleE2ETest.kt) | `CT-25 should accumulate two complete visits by same plate and isolate sector revenue` |
| CT-26 | EXIT sem PARKED dentro da gratuidade | [ExitEventIntegrationTest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/web/ExitEventIntegrationTest.kt) | `CT-26 should close unparked session free within tolerance without releasing another vehicle spot` |
| CT-27 | EXIT anterior à entrada preserva sessão e vaga | [ExitEventIntegrationTest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/web/ExitEventIntegrationTest.kt) | `CT-27 should reject invalid exit timestamp without changing session or spot` |
| CT-28 | Coordenada desconhecida não aloca outra vaga | [ParkedEventIntegrationTest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/web/ParkedEventIntegrationTest.kt) | `should reject nearby and distant unknown coordinates without allocating another sector` |
| CT-29 | Data UTC e fronteiras de meia-noite | [RevenueControllerIntegrationTest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/web/RevenueControllerIntegrationTest.kt) | `CT-29 should use inclusive UTC midnight and exclusive next midnight` |
| CT-30 | GET com body JSON e query retornam a mesma receita | [RevenueControllerIntegrationTest](../../src/test/kotlin/br/com/estapar/parking/infrastructure/web/RevenueControllerIntegrationTest.kt) | `CT-30 should accept JSON body and query parameters with identical revenue` |

## Coberturas adicionais

- `EntryOccupancyPricingTest`: proporções antes do arredondamento e limites numéricos.
- `PricingCalculatorTest`: nanossegundos, milissegundos, meia-noite e arredondamento apenas ao final.
- `ParkedEventIntegrationTest`: ocupação concorrente, duplicatas, tentativa de mover de vaga e rollback.
- `ExitEventIntegrationTest`: replays, visitas posteriores, disputa com ENTRY/PARKED e rollback da liberação.
- `RevenueControllerIntegrationTest`: sessões abertas excluídas, precisão decimal, relógio UTC, datas futuras e filtros inválidos/conflitantes.
- `ParkingLifecycleE2ETest`: ciclo completo com receita de 18.00, fronteira grátis/paga, histórico de visitas, capacidade, idempotência e receita no dia UTC da saída.

## Reprodução

Execute `./gradlew check` (ou `.\gradlew.bat check` no PowerShell), com JDK 21 e Docker em execução. O resultado deve ser observado nos relatórios JUnit e JaCoCo gerados, não inferido somente pela presença desta matriz. O gate é de cobertura de linhas; não é uma declaração de 85% de cobertura de requisitos.
