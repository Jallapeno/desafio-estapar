# Validação do Docker Compose com simulador real

Execução realizada em 2026-11-18, Docker Engine 29.9.3 e Docker Compose v5.7.0, com containers Linux no Docker Desktop/Windows. Branch criada a partir da develop `26c4d63`.

## Ambiente e reprodução

A validação usou o projeto Compose isolado `estapar-compose-check` e seu próprio volume. O volume local anterior não foi reutilizado nem removido. As credenciais foram fornecidas em `.env` ignorado pelo Git, fora do contexto da imagem.

```powershell
docker compose -p estapar-compose-check config --quiet
docker compose -p estapar-compose-check up -d --build --wait --wait-timeout 180
docker compose -p estapar-compose-check ps
docker compose -p estapar-compose-check logs --tail=100 simulator api
```

Imagem real: `cfontes0estapar/garage-sim:1.0.0`, digest `sha256:4a23388600c4fbee8cea6dd2b8924d5dc869309ccd7a04fc75cff686d574eb7f`. Nenhum simulador falso ou adaptador de eventos foi utilizado.

## Compatibilidade observada

- O processo da imagem escuta em 3000, apesar de declarar 8080 nos metadados; o Compose publica `8080:3000`.
- O binário usa `CLIENT_WEBHOOK_URL`, não a variável `EXTERNAL_API_URL` presente nos metadados da imagem. `/status` confirmou `http://api:3003/webhook`, entradas a cada 5 segundos e saídas a cada 15 segundos.
- O simulador só começa a emitir eventos após consultar `/garage`; essa chamada é feita pela API na inicialização.
- Os timestamps reais chegam sem offset, como `2026-11-18T18:02:01`. A extensão restrita aos campos temporais do webhook interpreta esse formato como UTC, preserva offsets explícitos e continua rejeitando precisão inferior a milissegundos.

## Evidências

Na amostra de 18:08:52 UTC, antes de qualquer evento controlado adicional:

- Sincronização de 2 setores e 30 vagas, com Flyway V1/V2 aplicadas.
- Logs do próprio simulador com `Entry successful`, `Parked successful` e `Exit successful`, sem eventos falhos na execução final.
- MySQL com 7 sessões `EXITED` e 14 `PARKED`, originadas exclusivamente no simulador. Contagens variam conforme o instante da consulta.
- Nenhuma divergência entre `parking_spots.occupied` e as sessões `PARKED` associadas.
- Receita de A/B igual à soma das sessões encerradas no MySQL. Essas visitas duraram segundos, portanto a receita zero respeita os 30 minutos grátis; o log de receita interna do simulador não foi usado como oráculo financeiro.
- HTTP 200 no OpenAPI, no Swagger UI e no JavaScript da interface.

Separadamente, com o simulador temporariamente pausado para evitar concorrência na conferência, foram enviados via HTTP eventos para a placa `COMPOSE-SMOKE`: entrada sem offset UTC, estacionamento em uma vaga livre real e saída com `Z`, após 60 minutos. Setor A, preço-base 40,50, multiplicador 1,00, cobrança 40,50; a sessão ficou `EXITED` e a vaga foi liberada. `GET /revenue` com query params e com body JSON retornou 40,50, igual ao MySQL. O simulador foi retomado após a conferência.

A API foi reiniciada pelo Compose: voltou saudável, sincronizou novamente e preservou a sessão encerrada e a cobrança de 40,50. O processo da imagem executou como UID/GID 10001.

Regressão: `gradlew.bat check` aprovado, 149 testes sem falhas e cobertura de linhas 97.29%. Inclui formato real sem offset, offsets explícitos, formatos inválidos, limite de precisão, escopo do desserializador e deduplicação entre representações equivalentes.

O ambiente de validação foi encerrado com `docker compose -p estapar-compose-check down`, preservando seu volume. Para a execução normal, use os comandos sem `-p`, conforme o README.
