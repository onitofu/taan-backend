package taan.online.room.protocol.packet;

public record PrGameSessionCreateFail(int reason) {
    public static final int TYPE_ID = 0x737f094f;
}
