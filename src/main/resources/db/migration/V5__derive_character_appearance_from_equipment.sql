LOCK TABLE characters, character_appearance_parts, inventories, inventory_items IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM characters c CROSS JOIN generate_series(0, 15) s(slot)
        LEFT JOIN character_appearance_parts p ON p.character_id = c.id AND p.slot = s.slot
        LEFT JOIN inventories i ON i.character_id = c.id AND i.kind = 'EQUIPMENT'
        LEFT JOIN inventory_items e ON e.inventory_id = i.id AND e.slot = s.slot
        WHERE p.slot IS NULL OR i.id IS NULL
            OR p.item_id IS DISTINCT FROM coalesce(e.item_id, 0)
            OR p.parameter0 IS DISTINCT FROM coalesce(e.parameter0, 0)
            OR p.parameter1 IS DISTINCT FROM coalesce(e.parameter1, 0)
            OR p.parameter2 IS DISTINCT FROM coalesce(e.parameter2, 0)
    ) OR EXISTS (
        SELECT 1 FROM inventory_items e JOIN inventories i ON i.id = e.inventory_id
        WHERE i.kind = 'EQUIPMENT' AND e.slot NOT BETWEEN 0 AND 15
    ) THEN
        RAISE EXCEPTION 'Character appearance and equipment disagree';
    END IF;
END $$;

DROP TABLE character_appearance_parts;
