package taan.online.channel.repository;

import java.util.List;
import taan.online.channel.model.ChannelGroupDefinition;

public interface ChannelRepository {
    List<ChannelGroupDefinition> load();
}
