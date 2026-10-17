\set ON_ERROR_STOP on
BEGIN;
SELECT set_config('taan.grant.character_id', :'character_id', true),
       set_config('taan.grant.item_id', :'item_id', true),
       set_config('taan.grant.palette_1_id', :'palette_1_id', true),
       set_config('taan.grant.palette_2_id', :'palette_2_id', true),
       set_config('taan.grant.palette_3_id', :'palette_3_id', true);
DO $$
DECLARE
    character_key BIGINT := current_setting('taan.grant.character_id')::BIGINT;
    definition_key BIGINT := current_setting('taan.grant.item_id')::BIGINT;
    owner_key BIGINT;
    bag_key BIGINT;
    free_slot INTEGER;
BEGIN
    SELECT account_id INTO STRICT owner_key FROM characters WHERE id = character_key AND deleted_at IS NULL;
    PERFORM id FROM accounts WHERE id = owner_key FOR UPDATE;
    PERFORM id FROM characters WHERE id = character_key AND account_id = owner_key AND deleted_at IS NULL FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Character is no longer active';
    END IF;
    PERFORM item_id FROM item_definitions WHERE item_id = definition_key AND resource_available;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Unknown or unavailable item definition';
    END IF;
    SELECT id INTO STRICT bag_key FROM inventories WHERE character_id = character_key AND kind = 'BAG' FOR UPDATE;
    SELECT free.slot INTO free_slot FROM generate_series(0, 79) AS free(slot)
    WHERE NOT EXISTS (SELECT 1 FROM inventory_items e WHERE e.inventory_id = bag_key AND e.slot = free.slot)
    ORDER BY free.slot LIMIT 1;
    IF free_slot IS NULL THEN
        RAISE EXCEPTION 'Character bag is full';
    END IF;
    INSERT INTO inventory_items (inventory_id, slot, item_id, quantity, palette_1_id, palette_2_id, palette_3_id)
    VALUES (bag_key, free_slot, definition_key, 1,
        current_setting('taan.grant.palette_1_id')::INTEGER,
        current_setting('taan.grant.palette_2_id')::INTEGER,
        current_setting('taan.grant.palette_3_id')::INTEGER);
END $$;
COMMIT;
