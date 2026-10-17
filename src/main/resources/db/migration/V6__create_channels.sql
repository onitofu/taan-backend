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
    enabled BOOLEAN NOT NULL DEFAULT true,
    min_level INTEGER NOT NULL DEFAULT 1,
    max_level INTEGER NOT NULL DEFAULT 109,
    capacity INTEGER NOT NULL DEFAULT 100 CHECK (capacity > 0),
    sort_order INTEGER NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    CHECK (min_level BETWEEN 1 AND 109 AND max_level BETWEEN min_level AND 109)
);

CREATE INDEX channels_group_id_idx ON channels(group_id);

WITH solar AS (INSERT INTO channel_groups (name) VALUES ('Solar') RETURNING id)
INSERT INTO channels (id, group_id, name, sort_order)
SELECT c.id, solar.id, c.name, c.sort_order
FROM solar CROSS JOIN (VALUES (1, 'Moon', 0), (2, 'Terra', 1)) c(id, name, sort_order);
