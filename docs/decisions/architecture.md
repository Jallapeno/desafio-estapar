# Decisões de arquitetura

## ADR-01 — Kotlin, Java 21 e Micronaut

O desafio pede Java 21, Kotlin 2.1.x, Spring ou Micronaut e MySQL. O projeto utiliza Kotlin 2.1.21, toolchain Java 21 e a plataforma Micronaut configurada em 4.7.6. A composição dos casos de uso fica em `ApplicationUseCaseFactory`. O plugin AOT está configurado; a entrega verificada usa JVM, sem afirmar validação de binário nativo.

## ADR-02 — Portas e adaptadores

Regras puras ficam no domínio. Serviços da aplicação dependem de interfaces e de `TransactionPort`, sem anotações transacionais do framework. Adaptadores JDBC e HTTP ficam na infraestrutura. Isso permite testar cálculo de tarifa sem banco e validar a mesma aplicação com MySQL real.

## ADR-03 — Persistência e precisão

Flyway versiona o schema. Preços e cobranças são decimais exatos; coordenadas usam `DECIMAL(10,6)`, e timestamps persistidos têm precisão de milissegundos (`DATETIME(3)`). O calculador preserva a precisão completa do `Duration`; payloads do simulador exemplificam milissegundos. Migrações existentes permanecem imutáveis.

## ADR-04 — Capacidade e lock compartilhado

ENTRY não informa vaga/setor. A sessão ENTERED reserva capacidade global; PARKED associa a vaga exata. ENTRY, PARKED e EXIT adquirem os setores em ordem antes das leituras. Esse lock global simplifica a consistência, serializando as transições da garagem; não foi realizado benchmark de throughput. Futuras transições devem preservar o protocolo de lock.

## ADR-05 — Lotação e cobrança

O multiplicador é decidido pela proporção exata anterior à entrada e salvo na sessão. A seleção não usa a razão arredondada para quatro casas. A gratuidade vai até 30 minutos exatos; ultrapassado o limite, todas as horas desde a entrada são cobradas com teto inteiro. Apenas o total monetário é arredondado com HALF_UP. O preço-base é consultado no setor na saída.

## ADR-06 — Correspondência exata

Um PARKED descreve a posição física observada; não representa um pedido para escolher uma vaga. Coordenadas desconhecidas e vagas ocupadas são rejeitadas sem alocar alternativa. Isso evita faturar pelo setor errado. Repetições da mesma vaga são idempotentes; realocações são recusadas.

## ADR-07 — UTC e receita

JVM de produção, conexão JDBC e MySQL do Compose são configurados em UTC. O serviço de receita também usa explicitamente UTC e um Clock injetável. A consulta filtra setor/status e o intervalo indexado `[início do dia, início do próximo dia)`, com corte inclusivo no instante da resposta. A receita é atribuída à saída, não à entrada.

## ADR-08 — Compatibilidade do GET

O enunciado mostra GET /revenue com body JSON. O endpoint oferece também query params. Body é lido pela API ByteBody no executor de bloqueio; valores conflitantes entre os formatos retornam 400. Os testes enviam GET real com body pelo cliente HTTP do JDK, sem depender de mocks de controller.

## ADR-09 — Sincronização e falhas

Configuração é buscada fora da transação, validada e persistida atomicamente. Upserts preservam a ocupação já conhecida. Rede recebe retentativas; dados inválidos não são persistidos. Esgotadas as tentativas de startup, a aplicação falha em vez de operar sem topologia. Não há agendador de recuperação em segundo plano.

## ADR-10 — Limites do contrato de eventos

HTTP 200 reconhece eventos válidos, inclusive duplicatas e conflitos de negócio registrados em log. Payloads inválidos retornam 400. A ausência de ID de evento e de timestamp em PARKED impede deduplicação global perfeita entre visitas; a garantia implementada é baseada na sessão ativa. A política de setor padrão para EXIT sem PARKED está em [US-08-exit.md](US-08-exit.md).

## ADR-11 — Verificação da entrega

JUnit 5, Micronaut Test e MySQL/Testcontainers exercitam domínio, integração e HTTP E2E. O gate JaCoCo considera linhas do código da aplicação, excluindo metadados gerados do framework, com mínimo de 85%. Cobertura de linhas não substitui os cenários de negócio: a matriz CT-01 a CT-30 e os asserts de banco/receita registram essa rastreabilidade.
