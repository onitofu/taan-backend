package taan.online.protocol.option.codec;

import java.util.Objects;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;
import taan.online.protocol.option.packet.OptionList;

public final class OptionListCodec {
    public OptionList decode(BinaryReader input) {
        if (input.unsignedInt() != OptionList.TYPE_ID) {
            throw new MalformedPacketException("UNEXPECTED_OPTION_LIST_TYPE");
        }

        if (input.unsignedInt() != 0) {
            throw new MalformedPacketException("UNSUPPORTED_NONEMPTY_OPTION_LIST");
        }

        return new OptionList();
    }

    public void encode(BinaryWriter output, OptionList options) {
        Objects.requireNonNull(options);
        output.unsignedInt(OptionList.TYPE_ID).unsignedInt(0);
    }
}
