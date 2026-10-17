package taan.online.channel.protocol.codec;

import java.util.List;
import java.util.Map;
import taan.online.channel.protocol.packet.PrGetChannelList;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.SBufferEncoder;

public final class ChannelListResponseCodec {
    public byte[] encode(PrGetChannelList response) {
        var tree = new BinaryWriter();
        node(tree, "content", List.of(), response.groups().size());

        for (var group : response.groups()) {
            node(
                tree,
                "server",
                List.of(
                    Map.entry("name", group.name()),
                    Map.entry(
                        "type",
                        Integer.toString(
                            group.type()
                        )
                    )
                ),
                group.channels().size()
            );

            for (var channel : group.channels()) {
                node(
                    tree,
                    "channel",
                    List.of(
                        Map.entry("id", Integer.toString(channel.id())),
                        Map.entry("name", channel.name()),
                        Map.entry("ip", "127.0.0.1"),
                        Map.entry("port", Integer.toString(response.port())),
                        Map.entry("minLevel", Integer.toString(channel.minLevel())),
                        Map.entry("maxLevel", Integer.toString(channel.maxLevel())),
                        Map.entry("fill", Integer.toString(channel.fill())),
                        Map.entry("disable", channel.enabled() ? "0" : "1")
                    ),
                    0
                );
            }
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
