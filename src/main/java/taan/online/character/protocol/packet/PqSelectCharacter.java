package taan.online.character.protocol.packet;

public record PqSelectCharacter(long characterId) {
    public static final int TYPE_ID = 0x3abb06ae;
}
