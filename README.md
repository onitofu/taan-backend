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
  <a href="#channel-types">Channel types</a> ·
  <a href="#channel-loot">Channel loot</a> ·
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

After the first start, configure the channel catalog in PostgreSQL. Migrations create a `Solar` group
with `Moon` and `Terra` channels as defaults. Open a database session:

```sh
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

- `channel_groups`: set `name`, `type` and `sort_order`.
- `channels`: link each channel through `group_id`; set its unique `id`, `name`, `kind`, `enabled`,
  `min_level`, `max_level`, `capacity` and `sort_order`. See [channel types](#channel-types).
- `channels.loot_picker_scalar`: configure the available battle items. See [channel loot](#channel-loot).

Exit psql with `\q`, then reload the catalog:

```sh
docker compose restart backend
```

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

## Channel types

Channel settings are stored in PostgreSQL. The client uses these numeric categories:

| Value | Group description (`channel_groups.type`) | Ordinary maps (`channels.kind`) |
| --- | --- | --- |
| `0` | Beginner (`BeginnerDesc`) | 1, 2, 4, 9, 10, 17 — 6 maps |
| `1` | Rookie (`RookieDesc`) | 1–12, 15–21 — 19 maps |
| `4` | Veteran (`VeteranDesc`) | 3, 5–8, 11–15, 18–21 — 14 maps |
| `6` | Free channel (`FreeChDesc`) | 1–21 — all 21 maps |

The fields are independent: `channel_groups.type` selects the group description;
`channels.kind` controls map availability. Level restrictions use `channels.min_level` and `channels.max_level`.
Restart the backend after changing these settings.

Map IDs and assets: [map catalog](src/main/resources/match/maps.csv).

## Channel loot

`channels.loot_picker_scalar` controls the native item pool and weights for boxes and end-of-turn bonuses
in Item and Captain Item matches. It is independent of the channel type.

| Native value | Available items | Backend support |
| --- | --- | --- |
| `0–49` | +1, POWER, homing and DamageUp cards | Supported |
| `50–69` | The same items, plus three-way spread | Supported |
| `70–89` | The same items, plus +2 | Supported |
| `90–100` | The same items, plus nuclear (item `4`) | Supported |

The supported range is `0–100`.
The default is `0` for existing and new channels. Weights vary with the value;
a higher value does not guarantee a particular item.
The migration preserves existing channels and their settings.

To enable spread and +2 for one channel, replace `1` with its ID:

```sql
UPDATE channels SET loot_picker_scalar = 70 WHERE id = 1;
```

Restart the backend to load changes: `docker compose restart backend`. Each new match keeps its channel's loaded
value for all turns.

Nuclear temporarily replaces the projectile weapon, preserving the character's base weapon. POWER combines with it;
personal special takes precedence. F9 or a turn timeout preserves activated items until a shot consumes them.
Set the channel scalar to `90–100` and restart the backend to enable nuclear drops.
