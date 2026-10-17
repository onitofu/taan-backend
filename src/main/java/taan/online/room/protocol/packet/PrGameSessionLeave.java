package taan.online.room.protocol.packet;

public record PrGameSessionLeave(int success) {
    public static final int TYPE_ID = 0x48ef076c;
}
