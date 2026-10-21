ALTER TABLE channels
    DROP CONSTRAINT channels_loot_picker_scalar_check,
    ADD CONSTRAINT channels_loot_picker_scalar_check CHECK (loot_picker_scalar BETWEEN 0 AND 100);
