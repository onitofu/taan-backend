<p align="center">
  <img src="assets/taan-logo.svg" alt="TAAN logo" width="360">
</p>

<h1 align="center">TAAN Backend</h1>

<p align="center">
  <strong>An independent server reconstruction for TAAN 1.929.</strong><br>
  Java 25 · PostgreSQL 18 · Docker Compose
</p>

<p align="center">
  <a href="#quick-start">Quick start</a> ·
  <a href="#commands">Commands</a> ·
  <a href="https://github.com/onitofu/taan-backend/actions/workflows/ci.yml">CI</a>
</p>

## Quick start

Requires Docker with Compose v2 and Make.

```sh
cp .env.example .env
```

Set `POSTGRES_PASSWORD` in `.env`, then start the backend and PostgreSQL:

```sh
make start
```

Default TCP endpoint: `127.0.0.1:2181`. Configuration is in `.env`.

```sh
docker compose logs -f backend
docker compose down
```

`docker compose down` stops the services and preserves database data.

## Commands

| Command | Action |
| --- | --- |
| `make build` | Build the backend Docker image. |
| `make start` | Build and start services in the background. |
| `make clean` | Stop services and **delete the PostgreSQL data volume**. |
| `make checkstyle` | Check Java code style. |
| `make test` | Run tests, including integration tests. |
| `make verify` | Run the full build: `./mvnw clean verify`. |

Maven commands require JDK 25; the Maven Wrapper is included. Tests require Docker for PostgreSQL integration tests.

Reports: `target/surefire-reports/` and `target/site/jacoco/` (HTML/XML).
