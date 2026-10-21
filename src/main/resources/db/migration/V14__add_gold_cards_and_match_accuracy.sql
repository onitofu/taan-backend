ALTER TABLE match_participant_results
    ADD COLUMN accepted_shots BIGINT CHECK (accepted_shots >= 0),
    ADD COLUMN damaging_shots BIGINT CHECK (damaging_shots >= 0),
    ADD COLUMN gold_before BIGINT CHECK (gold_before BETWEEN 0 AND 4294967295),
    ADD COLUMN gold_after BIGINT CHECK (gold_after BETWEEN 0 AND 4294967295),
    ADD COLUMN gold_base_delta BIGINT CHECK (gold_base_delta >= 0),
    ADD COLUMN gold_medal_delta BIGINT CHECK (gold_medal_delta >= 0),
    ADD COLUMN gold_card_delta BIGINT CHECK (gold_card_delta >= 0),
    ADD COLUMN experience_card_delta BIGINT NOT NULL DEFAULT 0 CHECK (experience_card_delta >= 0),
    ADD COLUMN card_draw SMALLINT CHECK (card_draw BETWEEN 0 AND 99),
    ADD COLUMN card_kind VARCHAR(16) CHECK (card_kind IN ('EXPERIENCE', 'GOLD')),
    ADD COLUMN card_amount BIGINT CHECK (card_amount BETWEEN 0 AND 4294967295),
    ADD CONSTRAINT match_participant_results_shots CHECK (
        num_nonnulls(accepted_shots, damaging_shots) IN (0, 2) AND damaging_shots <= accepted_shots
    ),
    ADD CONSTRAINT match_participant_results_gold_complete CHECK (
        num_nonnulls(gold_before, gold_after, gold_base_delta, gold_medal_delta, gold_card_delta) IN (0, 5)
    ),
    ADD CONSTRAINT match_participant_results_gold_delta CHECK (
        gold_after - gold_before = gold_base_delta + gold_medal_delta + gold_card_delta
    ),
    ADD CONSTRAINT match_participant_results_card_complete CHECK (
        num_nonnulls(card_draw, card_kind, card_amount) IN (0, 3)
    ),
    ADD CONSTRAINT match_participant_results_card_delta CHECK (
        CASE
            WHEN card_kind IS NULL THEN experience_card_delta = 0 AND COALESCE(gold_card_delta, 0) = 0
            WHEN card_kind = 'EXPERIENCE' THEN experience_before IS NOT NULL AND gold_card_delta IS NOT NULL
                AND experience_card_delta = card_amount AND gold_card_delta = 0
            WHEN card_kind = 'GOLD' THEN gold_card_delta IS NOT NULL
                AND experience_card_delta = 0 AND gold_card_delta = card_amount
            ELSE FALSE
        END
    ),
    DROP CONSTRAINT match_participant_results_experience_delta,
    ADD CONSTRAINT match_participant_results_experience_delta CHECK (
        experience_after - experience_before = experience_base_delta + experience_pcroom_delta
            + experience_medal_delta + experience_card_delta
        AND level_after >= level_before
    );
