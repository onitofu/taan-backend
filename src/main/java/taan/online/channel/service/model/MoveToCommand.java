package taan.online.channel.service.model;

public record MoveToCommand(int mode, int channelId, long followArgument, long clientRevision) {
}
