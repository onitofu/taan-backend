DO $$
BEGIN
    IF EXISTS (
        SELECT c.id FROM characters c LEFT JOIN character_appearance_parts p ON p.character_id = c.id
        GROUP BY c.id HAVING count(p.slot) <> 16
    ) THEN
        RAISE EXCEPTION 'Incomplete character appearance';
    END IF;
END $$;

CREATE TABLE inventories (
    id BIGINT GENERATED ALWAYS AS IDENTITY (START WITH 1 MAXVALUE 4294967295 NO CYCLE) PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE RESTRICT,
    kind TEXT NOT NULL CHECK (kind IN ('BAG', 'EQUIPMENT', 'JEWEL_BOX')),
    UNIQUE (character_id, kind),
    CHECK (id BETWEEN 1 AND 4294967295)
);

CREATE TABLE inventory_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY (START WITH 1 NO CYCLE) PRIMARY KEY CHECK (id > 0),
    inventory_id BIGINT NOT NULL REFERENCES inventories(id) ON DELETE RESTRICT,
    slot SMALLINT NOT NULL CHECK (slot BETWEEN 0 AND 255),
    item_id BIGINT NOT NULL CHECK (item_id BETWEEN 1 AND 4294967295),
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity BETWEEN 1 AND 65535),
    parameter0 INTEGER NOT NULL CHECK (parameter0 BETWEEN 0 AND 65535),
    parameter1 INTEGER NOT NULL CHECK (parameter1 BETWEEN 0 AND 65535),
    parameter2 INTEGER NOT NULL CHECK (parameter2 BETWEEN 0 AND 65535),
    UNIQUE (inventory_id, slot)
);

INSERT INTO inventories (character_id, kind)
SELECT c.id, k.kind FROM characters c CROSS JOIN (VALUES ('BAG'), ('EQUIPMENT'), ('JEWEL_BOX')) k(kind)
ORDER BY c.id, k.kind;

INSERT INTO inventory_items (inventory_id, slot, item_id, parameter0, parameter1, parameter2)
SELECT i.id, p.slot, p.item_id, p.parameter0, p.parameter1, p.parameter2
FROM inventories i JOIN character_appearance_parts p ON p.character_id = i.character_id
WHERE i.kind = 'EQUIPMENT' AND p.item_id <> 0
ORDER BY i.id, p.slot;
