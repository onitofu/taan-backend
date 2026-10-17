package taan.online.character.service.model;

public enum CreateCharacterOutcome {
    CREATED,
    NAME_TAKEN,
    ACCOUNT_HAS_CHARACTERS,
    INVALID_NAME,
    INVALID_APPEARANCE,
    SESSION_CLOSED
}
