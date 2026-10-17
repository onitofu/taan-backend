CREATE TABLE characters (
    id BIGINT GENERATED ALWAYS AS IDENTITY (START WITH 1 MAXVALUE 4294967295 NO CYCLE) PRIMARY KEY,
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    name VARCHAR(18) COLLATE "C" NOT NULL UNIQUE,
    level INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT characters_id_range CHECK (id BETWEEN 1 AND 4294967295),
    CONSTRAINT characters_name_format CHECK (
        char_length(name) BETWEEN 4 AND 18
        AND name COLLATE "C" ~ U&'^[0-9A-z\00C0-\017F\0180-\0236\1EA0-\1EF9]+$'
    ),
    CONSTRAINT characters_level_range CHECK (level BETWEEN 1 AND 65535)
);

CREATE INDEX characters_account_id_idx ON characters(account_id);

CREATE TABLE character_appearance_parts (
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE RESTRICT,
    slot SMALLINT NOT NULL,
    item_id BIGINT NOT NULL,
    parameter0 INTEGER NOT NULL,
    parameter1 INTEGER NOT NULL,
    parameter2 INTEGER NOT NULL,
    PRIMARY KEY (character_id, slot),
    CONSTRAINT character_appearance_slot_range CHECK (slot BETWEEN 0 AND 15),
    CONSTRAINT character_appearance_item_range CHECK (item_id BETWEEN 0 AND 4294967295),
    CONSTRAINT character_appearance_parameter0_range CHECK (parameter0 BETWEEN 0 AND 65535),
    CONSTRAINT character_appearance_parameter1_range CHECK (parameter1 BETWEEN 0 AND 65535),
    CONSTRAINT character_appearance_parameter2_range CHECK (parameter2 BETWEEN 0 AND 65535)
);
