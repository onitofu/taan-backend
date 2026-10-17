package taan.online.account.protocol.codec;

import taan.online.account.protocol.packet.PqCreateAccount;
import taan.online.protocol.BinaryReader;

public final class CreateAccountRequestCodec {
    public PqCreateAccount decode(byte[] root) {
        var reader = new BinaryReader(root, PqCreateAccount.TYPE_ID);
        var request = new PqCreateAccount(
            reader.wideString(),
            reader.wideString(),
            reader.unsignedByte(),
            reader.unsignedByte()
        );

        reader.finish();
        return request;
    }
}
