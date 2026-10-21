package taan.online.match.protocol.packet;

public record ResultBonus(int kind, int expBonus, int goldBonus) {
    public static final int TYPE_ID = 0x1a840486;
}
