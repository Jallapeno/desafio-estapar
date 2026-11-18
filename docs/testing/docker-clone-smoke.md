# Validação de clone e execução sem configuração

Data: 2026-11-18T21:44:08.942537+00:00. Código de configuração testado: `94c0e41`.

Foi criado um clone Git local independente, com `git clone --no-local --depth 1 --single-branch --branch codex/feature-docker-quickstart`, a partir do repositório de trabalho. O clone não continha `.env`, `build/` ou `.gradle/`. A origem foi local para testar a revisão ainda não publicada; isso não verifica permissões de acesso ao GitHub dos avaliadores.

## Verificações

- `docker compose config --format json`, sem variáveis de credenciais herdadas: credenciais padrão coerentes entre MySQL e API; todas as portas publicadas vinculadas a `127.0.0.1`; simulador definido como `linux/amd64`.
- Sobrescritas opcionais de usuário/senhas aplicadas consistentemente nos dois serviços, verificadas por configuração resolvida.
- `docker compose -p estapar-recruiter-check up -d --build --wait --wait-timeout 180` com volume novo: inicialização aprovada sem criar `.env` nem executar ferramentas Java/Gradle no host.
- Build repetido com `docker compose -p estapar-recruiter-check build --no-cache api`: compilação Gradle/JDK dentro do container aprovada em aproximadamente 2 minutos, sem reutilizar camadas de compilação. As imagens-base já estavam disponíveis no Docker local.
- API e MySQL saudáveis após atualizar para a imagem reconstruída; Swagger UI, OpenAPI YAML e `GET /revenue?sector=A` retornando HTTP 200.
- Simulador real com `Entry successful`, `Parked successful` e `Exit successful`; 2 setores, 30 vagas e 2 migrations aplicadas.

Amostra do banco, com valores que variam conforme a duração da simulação:

```text
2
30
2
EXITED	14
PARKED	27
```

O teste usou portas publicadas alternativas (`SERVER_PORT=13003`, `DB_PORT=13306`, `SIMULATOR_PORT=18080`) exclusivamente para coexistir com o ambiente manual já ativo. As portas padrão foram conferidas no modelo Compose resolvido. Nenhuma configuração de credencial foi necessária.

Ambiente: Docker Desktop/Windows, Docker Engine 29.9.3, Compose v5.7.0 e containers Linux amd64. Hardware ARM e outros sistemas operacionais não foram executados neste teste; em ARM, a imagem amd64 do simulador depende da emulação oferecida pelo Docker.

Os containers de validação foram encerrados com `docker compose -p estapar-recruiter-check down`, preservando seu volume. O ambiente manual `estapar-compose-check` não foi interrompido.

## Publicação

Esta validação cobre os arquivos versionados da branch de trabalho. Na conferência remota, a branch padrão `main` estava em `af9601b`, sem Dockerfile. Para que um clone padrão entregue este quickstart aos recrutadores, é necessário integrar e publicar a versão validada na branch padrão antes de enviar o repositório para avaliação.
