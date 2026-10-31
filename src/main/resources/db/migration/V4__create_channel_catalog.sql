CREATE TABLE channel_types (
    id SMALLINT PRIMARY KEY CHECK (id IN (0, 1, 4, 6)),
    name VARCHAR(32) NOT NULL UNIQUE CHECK (btrim(name) <> '')
);

INSERT INTO channel_types (id, name) VALUES
(0, 'BEGINNER'),
(1, 'ROOKIE'),
(4, 'VETERAN'),
(6, 'FREE');

CREATE TABLE channel_groups (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY CHECK (id > 0),
    name VARCHAR(64) NOT NULL CHECK (btrim(name) <> ''),
    type SMALLINT NOT NULL DEFAULT 0 CHECK (type BETWEEN 0 AND 6),
    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0)
);

CREATE TABLE channels (
    id INTEGER PRIMARY KEY CHECK (id BETWEEN 1 AND 65534),
    group_id INTEGER NOT NULL REFERENCES channel_groups(id) ON DELETE RESTRICT,
    name VARCHAR(64) NOT NULL CHECK (btrim(name) <> ''),
    kind SMALLINT NOT NULL DEFAULT 0 REFERENCES channel_types(id) ON DELETE RESTRICT,
    loot_picker_scalar SMALLINT NOT NULL DEFAULT 0 CHECK (loot_picker_scalar BETWEEN 0 AND 100),
    enabled BOOLEAN NOT NULL DEFAULT true,
    min_level INTEGER NOT NULL DEFAULT 1,
    max_level INTEGER NOT NULL DEFAULT 109,
    capacity INTEGER NOT NULL DEFAULT 100 CHECK (capacity > 0),
    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    CHECK (min_level BETWEEN 1 AND 109 AND max_level BETWEEN min_level AND 109)
);

CREATE INDEX channels_group_id_idx ON channels(group_id);

WITH astra AS (INSERT INTO channel_groups (name, type, sort_order) VALUES ('Astra', 0, 0) RETURNING id)
INSERT INTO channels (
    id, group_id, name, enabled, min_level, max_level, capacity, sort_order, kind, loot_picker_scalar
)
SELECT c.id, astra.id, c.name, true, 1, 109, 100, c.sort_order, c.kind, 100
FROM astra CROSS JOIN (VALUES
    (1, 'Sirius', 0, 0),
    (2, 'Vega', 1, 1),
    (3, 'Rigel', 2, 4),
    (4, 'Altair', 3, 6)
) c(id, name, sort_order, kind);
