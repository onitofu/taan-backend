CREATE TABLE match_results (
    match_id UUID PRIMARY KEY,
    finished_at TIMESTAMPTZ NOT NULL,
    winning_team SMALLINT CHECK (winning_team IN (1, 2)),
    cause VARCHAR(32) NOT NULL,
    policy_version VARCHAR(32) NOT NULL,
    reward_duration_millis BIGINT CHECK (reward_duration_millis BETWEEN 0 AND 1800000),
    CONSTRAINT match_results_cause CHECK (
        cause IN ('ELIMINATION', 'SURRENDER', 'DEPARTURE', 'TECHNICAL_CANCELLATION')
    ),
    CONSTRAINT match_results_winner CHECK (
        (cause = 'TECHNICAL_CANCELLATION' AND winning_team IS NULL)
        OR (cause <> 'TECHNICAL_CANCELLATION' AND winning_team IS NOT NULL)
    )
);

CREATE TABLE match_participant_results (
    match_id UUID NOT NULL REFERENCES match_results(match_id) ON DELETE RESTRICT,
    character_id BIGINT NOT NULL REFERENCES characters(id) ON DELETE RESTRICT,
    slot_index SMALLINT NOT NULL CHECK (slot_index BETWEEN 0 AND 7),
    team SMALLINT NOT NULL CHECK (team IN (1, 2)),
    damage BIGINT NOT NULL CHECK (damage BETWEEN 0 AND 4294967295),
    award_eligible BOOLEAN NOT NULL,
    force_medal BOOLEAN NOT NULL,
    kills SMALLINT CHECK (kills BETWEEN 0 AND 255),
    kill_medal BOOLEAN NOT NULL DEFAULT false,
    experience_before BIGINT CHECK (experience_before BETWEEN 0 AND 1048576),
    experience_after BIGINT CHECK (experience_after BETWEEN 0 AND 1048576),
    level_before SMALLINT CHECK (level_before BETWEEN 1 AND 109),
    level_after SMALLINT CHECK (level_after BETWEEN 1 AND 109),
    experience_base_delta BIGINT,
    experience_pcroom_delta BIGINT CHECK (experience_pcroom_delta >= 0),
    experience_medal_delta BIGINT CHECK (experience_medal_delta >= 0),
    experience_guard_adjustment BIGINT,
    force_medals_after BIGINT CHECK (force_medals_after BETWEEN 0 AND 4294967295),
    kill_medals_after BIGINT CHECK (kill_medals_after BETWEEN 0 AND 4294967295),
    supply_medals_after BIGINT CHECK (supply_medals_after BETWEEN 0 AND 4294967295),
    accepted_shots BIGINT CHECK (accepted_shots >= 0),
    damaging_shots BIGINT CHECK (damaging_shots >= 0),
    gold_before BIGINT CHECK (gold_before BETWEEN 0 AND 4294967295),
    gold_after BIGINT CHECK (gold_after BETWEEN 0 AND 4294967295),
    gold_base_delta BIGINT CHECK (gold_base_delta >= 0),
    gold_medal_delta BIGINT CHECK (gold_medal_delta >= 0),
    gold_card_delta BIGINT CHECK (gold_card_delta >= 0),
    experience_card_delta BIGINT NOT NULL DEFAULT 0 CHECK (experience_card_delta >= 0),
    card_draw SMALLINT CHECK (card_draw BETWEEN 0 AND 99),
    card_kind VARCHAR(16) CHECK (card_kind IN ('EXPERIENCE', 'GOLD')),
    card_amount BIGINT CHECK (card_amount BETWEEN 0 AND 4294967295),
    PRIMARY KEY (match_id, character_id),
    UNIQUE (match_id, slot_index),
    CONSTRAINT match_participant_results_medal CHECK (NOT force_medal OR (award_eligible AND damage > 0)),
    CONSTRAINT match_participant_results_kill_medal CHECK (
        NOT kill_medal OR (award_eligible AND kills IS NOT NULL AND kills >= 2)
    ),
    CONSTRAINT match_participant_results_experience_complete CHECK (
        num_nonnulls(experience_before, experience_after, level_before, level_after,
            experience_base_delta, experience_pcroom_delta, experience_medal_delta,
            experience_guard_adjustment, force_medals_after, kill_medals_after, supply_medals_after) IN (0, 11)
    ),
    CONSTRAINT match_participant_results_shots CHECK (
        num_nonnulls(accepted_shots, damaging_shots) IN (0, 2) AND damaging_shots <= accepted_shots
    ),
    CONSTRAINT match_participant_results_gold_complete CHECK (
        num_nonnulls(gold_before, gold_after, gold_base_delta, gold_medal_delta, gold_card_delta) IN (0, 5)
    ),
    CONSTRAINT match_participant_results_gold_delta CHECK (
        gold_after - gold_before = gold_base_delta + gold_medal_delta + gold_card_delta
    ),
    CONSTRAINT match_participant_results_card_complete CHECK (
        num_nonnulls(card_draw, card_kind, card_amount) IN (0, 3)
    ),
    CONSTRAINT match_participant_results_card_delta CHECK (
        CASE
            WHEN card_kind IS NULL THEN experience_card_delta = 0 AND COALESCE(gold_card_delta, 0) = 0
            WHEN card_kind = 'EXPERIENCE' THEN experience_before IS NOT NULL AND gold_card_delta IS NOT NULL
                AND experience_card_delta = card_amount AND gold_card_delta = 0
            WHEN card_kind = 'GOLD' THEN gold_card_delta IS NOT NULL
                AND experience_card_delta = 0 AND gold_card_delta = card_amount
            ELSE FALSE
        END
    ),
    CONSTRAINT match_participant_results_experience_delta CHECK (
        experience_after - experience_before = experience_base_delta + experience_pcroom_delta
            + experience_medal_delta + experience_card_delta
        AND level_after >= level_before
    )
);
