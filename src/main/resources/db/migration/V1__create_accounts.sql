CREATE TABLE accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY (START WITH 1 MAXVALUE 4294967295 NO CYCLE) PRIMARY KEY,
    login VARCHAR(50) COLLATE "C" NOT NULL UNIQUE,
    credential_verifier TEXT NOT NULL,
    gender SMALLINT NOT NULL,
    cash BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT accounts_cash_range CHECK (cash BETWEEN 0 AND 4294967295),
    CONSTRAINT accounts_id_range CHECK (id BETWEEN 1 AND 4294967295),
    CONSTRAINT accounts_login_format CHECK (
        char_length(login) BETWEEN 1 AND 50
        AND login COLLATE "C" ~ '^[!-~]+$'
        AND login = translate(login, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')
    ),
    CONSTRAINT accounts_gender CHECK (gender IN (1, 2))
);
