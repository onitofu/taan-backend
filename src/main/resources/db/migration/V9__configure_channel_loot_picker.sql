ALTER TABLE channels
    ADD COLUMN loot_picker_scalar SMALLINT NOT NULL DEFAULT 0 CHECK (loot_picker_scalar BETWEEN 0 AND 89);
