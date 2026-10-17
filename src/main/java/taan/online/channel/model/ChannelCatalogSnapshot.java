package taan.online.channel.model;

import java.util.Map;

public record ChannelCatalogSnapshot(ChannelDirectory directory, Map<Integer, Integer> fills) {
    public ChannelCatalogSnapshot {
        fills = Map.copyOf(fills);
    }
}
