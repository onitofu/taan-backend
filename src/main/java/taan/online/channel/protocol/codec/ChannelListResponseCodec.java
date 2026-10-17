package taan.online.channel.protocol.codec;

import java.util.List;
import java.util.Map;
import taan.online.channel.protocol.packet.PrGetChannelList;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.SBufferEncoder;

public final class ChannelListResponseCodec {
    public byte[] encode(PrGetChannelList response) {
        var tree = new BinaryWriter();
        node(tree, "content", List.of(), 1);
        node(
            tree,
            "server",
            List.of(Map.entry("name", response.groupName()), Map.entry("type", "0")),
            response.channels().size()
        );

        for (var channel : response.channels()) {
            node(
                tree,
                "channel",
                List.of(
                    Map.entry("id", Integer.toString(channel.id())),
                    Map.entry("name", channel.name()),
                    Map.entry("ip", "127.0.0.1"),
                    Map.entry("port", Integer.toString(response.port())),
                    Map.entry("minLevel", "0"),
                    Map.entry("maxLevel", "65535"),
                    Map.entry("fill", "0"),
                    Map.entry("disable", "1")
                ),
                0
            );
        }

        byte[] envelope = new SBufferEncoder().encode(tree.toByteArray());
        return new BinaryWriter().unsignedInt(PrGetChannelList.TYPE_ID).unsignedInt(envelope.length)
            .bytes(envelope).toByteArray();
    }

    private void node(BinaryWriter output, String tag, List<Map.Entry<String, String>> attributes, int children) {
        output.wideString(tag).wideString("").unsignedInt(attributes.size());

        for (var attribute : attributes) {
            output.wideString(attribute.getKey()).wideString(attribute.getValue());
        }

        output.unsignedInt(children);
    }
}
