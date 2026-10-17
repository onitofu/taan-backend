package taan.online.channel.controller;

import taan.online.channel.model.ChannelCatalogSnapshot;
import taan.online.channel.protocol.packet.ChannelEntry;
import taan.online.channel.protocol.packet.ChannelGroupEntry;
import taan.online.channel.protocol.packet.PrGetChannelList;

public final class ChannelCatalogMapper {
    public PrGetChannelList map(ChannelCatalogSnapshot snapshot) {
        var groups = snapshot.directory().groups().stream().map(
            group -> new ChannelGroupEntry(
                group.name(),
                group.type(),
                group.channels().stream().map(
                    channel -> new ChannelEntry(
                        channel.id(),
                        channel.name(),
                        channel.enabled(),
                        channel.minLevel(),
                        channel.maxLevel(),
                        snapshot.fills().get(channel.id())
                    )
                ).toList()
            )
        ).toList();
        return new PrGetChannelList(groups, snapshot.directory().port());
    }
}
