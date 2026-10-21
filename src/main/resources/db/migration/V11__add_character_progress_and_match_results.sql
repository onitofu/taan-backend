ALTER TABLE characters
    ADD COLUMN experience BIGINT NOT NULL DEFAULT 0 CHECK (experience BETWEEN 0 AND 4294967295),
    ADD COLUMN force_medals BIGINT NOT NULL DEFAULT 0 CHECK (force_medals BETWEEN 0 AND 4294967295),
    ADD COLUMN kill_medals BIGINT NOT NULL DEFAULT 0 CHECK (kill_medals BETWEEN 0 AND 4294967295),
    ADD COLUMN supply_medals BIGINT NOT NULL DEFAULT 0 CHECK (supply_medals BETWEEN 0 AND 4294967295);

CREATE TABLE match_results (
    match_id UUID PRIMARY KEY,
    finished_at TIMESTAMPTZ NOT NULL,
    winning_team SMALLINT CHECK (winning_team IN (1, 2)),
    cause VARCHAR(32) NOT NULL,
    policy_version VARCHAR(32) NOT NULL,
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
    PRIMARY KEY (match_id, character_id),
    UNIQUE (match_id, slot_index),
    CONSTRAINT match_participant_results_medal CHECK (NOT force_medal OR (award_eligible AND damage > 0))
);
