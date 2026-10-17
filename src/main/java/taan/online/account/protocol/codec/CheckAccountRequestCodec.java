package taan.online.account.protocol.codec;

import taan.online.account.protocol.packet.PqCheckAccount;
import taan.online.protocol.BinaryReader;

public final class CheckAccountRequestCodec {
    public PqCheckAccount decode(byte[] root) {
        var reader = new BinaryReader(root, PqCheckAccount.TYPE_ID);
        var request = new PqCheckAccount(reader.wideString(), reader.unsignedByte());

        reader.finish();
        return request;
    }
}
