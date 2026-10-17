package taan.online.character.model;

public record CharacterId(long value) {
    public CharacterId {
        if (value < 1 || value > 0xffff_ffffL) {
            throw new IllegalArgumentException("Character identity is outside the wire range");
        }
    }
}
