package taan.online.room.protocol.packet;

public record PrGameSessionEnterFail(int reason) {
    public static final int TYPE_ID = 0x6a8108f9;
}
