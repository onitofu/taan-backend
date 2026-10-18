package taan.online.match.protocol.packet;

public record UnsupportedGameplayPacket(int typeId) {
    public static final int MOVE_TYPE_ID = 0x2c3c05b9;
    public static final int PASS_TURN_TYPE_ID = 0x470b0762;
    public static final int USE_ITEM_TYPE_ID = 0x3f8506de;
    public static final int SCENARIO_TYPE_ID = 0x46de0756;
}
