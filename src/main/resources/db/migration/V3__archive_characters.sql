ALTER TABLE characters ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE characters DROP CONSTRAINT characters_name_key;

CREATE UNIQUE INDEX characters_active_name_idx ON characters (name) WHERE deleted_at IS NULL;
