<p align="center">
  <img src="assets/taan-logo.svg" alt="TAAN logo" width="360">
</p>

<h1 align="center">TAAN Backend</h1>

<p align="center">
  <strong>Local server for TAAN Online.</strong><br>
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

On first start, migrations create the `Astra` group (`type = 0`) with four enabled channels:

| ID | Channel | Type |
| --- | --- | --- |
| `1` | Sirius | `BEGINNER` |
| `2` | Vega | `ROOKIE` |
| `3` | Rigel | `VETERAN` |
| `4` | Altair | `FREE` |

All four accept levels `1–109`, have a capacity of `100` and use `loot_picker_scalar = 100`.
To customize the catalog, open a database session:

```sh
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

- `channel_groups`: set `name`, `type` and `sort_order`.
- `channel_types`: reference values for `channels.kind`; see [channel types](#channel-types).
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

`channels.kind` references `channel_types.id`:

| Value | `channel_types.name` | Ordinary maps |
| --- | --- | --- |
| `0` | `BEGINNER` | 1, 2, 4, 9, 10, 17 — 6 maps |
| `1` | `ROOKIE` | 1–12, 15–21 — 19 maps |
| `4` | `VETERAN` | 3, 5–8, 11–15, 18–21 — 14 maps |
| `6` | `FREE` | 1–21 — all 21 maps |

The fields are independent: `channel_groups.type` selects the group description;
`channels.kind` controls map availability. Level restrictions use `channels.min_level` and `channels.max_level`.
Restart the backend after changing these settings.

Map IDs and assets: [map catalog](src/main/resources/match/maps.csv).

## Channel loot

`channels.loot_picker_scalar` controls the native item pool and weights for boxes and end-of-turn bonuses
in Item and Captain Item matches. It is independent of the channel type.

| Value | Available items |
| --- | --- |
| `0–49` | +1, POWER, homing and DamageUp cards |
| `50–69` | The same items, plus three-way spread |
| `70–89` | The same items, plus +2 |
| `90–100` | The same items, plus nuclear (item `4`) |

The four channels created by migrations use `100`; new channels default to `0` when the field is omitted.
Weights vary with the value; a higher value does not guarantee a particular item.

To enable spread and +2 for one channel, replace `1` with its ID:

```sql
UPDATE channels SET loot_picker_scalar = 70 WHERE id = 1;
```

Restart the backend to load changes: `docker compose restart backend`. Each new match keeps its channel's loaded
value for all turns.

Nuclear temporarily replaces the projectile weapon, preserving the character's base weapon. POWER combines with it;
personal special takes precedence. F9 or a turn timeout preserves activated items until a shot consumes them.
