package taan.online.lobby.protocol.packet;

public record UserSimpleInfo(long characterId, String name, int level, int flag) {
    public static final int TYPE_ID = 0x29b00595;
}
