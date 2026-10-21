package taan.online.channel.service;

import static taan.online.logging.LogField.accountId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.characterId;
import static taan.online.logging.LogField.previousChannelId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.recipientSessionId;
import static taan.online.logging.LogField.sessionId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import taan.online.channel.logging.ChannelLogEvent;
import taan.online.channel.model.ChannelCatalogSnapshot;
import taan.online.channel.model.ChannelDefinition;
import taan.online.channel.model.ChannelDirectory;
import taan.online.channel.model.ChannelGroupDefinition;
import taan.online.channel.model.ChannelParticipant;
import taan.online.channel.model.ChannelPresencePhase;
import taan.online.channel.model.ChannelSnapshot;
import taan.online.channel.service.model.ChannelMembershipSnapshot;
import taan.online.channel.service.model.ChannelSubscriptionResult;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.channel.service.model.MoveToOutcome;
import taan.online.channel.service.model.MoveToResult;
import taan.online.character.model.CharacterId;
import taan.online.logging.EventLogger;

public final class ChannelRegistry {
    private static final EventLogger LOG = EventLogger.forClass(ChannelRegistry.class);

    private final SelectedCharacterAccess sessions;
    private final int port;
    private final Map<UUID, ChannelMembership> members = new LinkedHashMap<>();
    private ChannelDirectory directory;

    public ChannelRegistry(SelectedCharacterAccess sessions, int port) {
        this.sessions = sessions;
        this.port = port;
    }

    public synchronized void initialize(List<ChannelGroupDefinition> groups) {
        if (directory != null) {
            throw new IllegalStateException("Channel configuration already loaded");
        }

        directory = new ChannelDirectory(groups, port);
    }

    public synchronized ChannelCatalogSnapshot catalog() {
        Objects.requireNonNull(directory, "Channel configuration not loaded");
        var fills = new HashMap<Integer, Integer>();
        directory.channels().forEach(channel -> fills.put(channel.id(), fill(channel)));
        return new ChannelCatalogSnapshot(directory, fills);
    }

    public synchronized int kind(int channelId) {
        return directory.channels().stream().filter(channel -> channel.id() == channelId)
            .findFirst().orElseThrow().kind();
    }

    public synchronized int lootPickerScalar(int channelId) {
        return directory.channels().stream().filter(channel -> channel.id() == channelId)
            .findFirst().orElseThrow().lootPickerScalar();
    }

    public synchronized Optional<ChannelMembershipSnapshot> membership(UUID id) {
        var member = members.get(id);

        if (member == null || !sessions.hasSelectedCharacter(id)) {
            return Optional.empty();
        }

        return Optional.of(new ChannelMembershipSnapshot(
            member.channelId,
            member.phase,
            member.participant,
            kind(member.channelId)
        ));
    }

    public synchronized MoveToResult move(UUID sessionId, MoveToCommand command) {
        var player = sessions.selectedCharacter(sessionId);

        if (player.isEmpty()) {
            return new MoveToResult(MoveToOutcome.IGNORED, command.channelId(), null);
        }

        ChannelDefinition target = directory.channels().stream()
            .filter(channel -> channel.id() == command.channelId()).findFirst().orElse(null);
        MoveToOutcome outcome;

        if (command.mode() != 0 || command.followArgument() != 0 || command.clientRevision() != 929) {
            outcome = MoveToOutcome.UNSUPPORTED;
        } else if (target == null) {
            outcome = MoveToOutcome.UNKNOWN;
        } else if (!target.enabled()) {
            outcome = MoveToOutcome.DISABLED;
        } else if (player.orElseThrow().level() < target.minLevel()) {
            outcome = MoveToOutcome.LEVEL_TOO_LOW;
        } else if (player.orElseThrow().level() > target.maxLevel()) {
            outcome = MoveToOutcome.LEVEL_TOO_HIGH;
        } else {
            releaseStaleCharacter(player.orElseThrow());
            var old = members.get(sessionId);
            boolean alreadyMember = old != null && old.channelId == target.id();
            outcome = !alreadyMember && population(target.id()) >= target.capacity()
                ? MoveToOutcome.FULL : MoveToOutcome.MOVED;
        }

        var participant = player.orElseThrow();

        if (outcome == MoveToOutcome.MOVED) {
            var old = members.get(sessionId);
            unsubscribe(old);
            members.put(sessionId, new ChannelMembership(participant, command.channelId()));
            LOG.log(
                ChannelLogEvent.MOVE_SUCCEEDED,
                sessionId(sessionId),
                accountId(participant.accountId().value()),
                characterId(participant.characterId().value()),
                channelId(command.channelId()),
                previousChannelId(old == null ? null : old.channelId)
            );
        } else {
            LOG.log(
                ChannelLogEvent.MOVE_REJECTED,
                sessionId(sessionId),
                accountId(participant.accountId().value()),
                characterId(participant.characterId().value()),
                channelId(command.channelId()),
                reason(outcome.name())
            );
        }

        return new MoveToResult(
            outcome,
            command.channelId(),
            target == null ? null : new ChannelSnapshot(target, fill(target))
        );
    }

    public synchronized ChannelSubscriptionResult enter(UUID sessionId, ChannelPresenceOutput output) {
        if (!sessions.hasSelectedCharacter(sessionId)) {
            return new ChannelSubscriptionResult(
                ChannelPresencePhase.CLOSED,
                0,
                CompletableFuture.completedFuture(null)
            );
        }

        var member = members.get(sessionId);

        if (member == null) {
            return new ChannelSubscriptionResult(ChannelPresencePhase.NONE, 0, output.unavailable());
        }

        boolean newlyVisible = member.phase != ChannelPresencePhase.LOBBY;
        member.phase = ChannelPresencePhase.LOBBY;
        member.output = output;
        var snapshot = members.values().stream()
            .filter(value -> value.channelId == member.channelId && value.phase == ChannelPresencePhase.LOBBY)
            .map(value -> value.participant).toList();
        int count = population(member.channelId);
        var delivery = output.initialize(count, snapshot);

        if (!sessions.hasSelectedCharacter(sessionId) || members.get(sessionId) != member) {
            release(sessionId, "SESSION_CLOSED_DURING_ENTRY");
            return new ChannelSubscriptionResult(ChannelPresencePhase.CLOSED, 0, delivery);
        }

        if (newlyVisible) {
            publish(member, List.of(member.participant), List.of());
        }

        return new ChannelSubscriptionResult(ChannelPresencePhase.LOBBY, count, delivery);
    }

    public synchronized ChannelSubscriptionResult leave(UUID sessionId, ChannelPresenceOutput output) {
        if (!sessions.hasSelectedCharacter(sessionId)) {
            return new ChannelSubscriptionResult(
                ChannelPresencePhase.CLOSED,
                0,
                CompletableFuture.completedFuture(null)
            );
        }

        var member = members.get(sessionId);
        unsubscribe(member);
        return new ChannelSubscriptionResult(
            member == null ? ChannelPresencePhase.NONE : ChannelPresencePhase.MEMBER,
            member == null ? 0 : population(member.channelId),
            output.left()
        );
    }

    public synchronized void hideFromLobby(UUID sessionId) {
        unsubscribe(members.get(sessionId));
    }

    public void roomChanged(int channelId, int roomId) {
        notifyRooms(channelId, roomId);
    }

    public void roomsReplaced(int channelId) {
        notifyRooms(channelId, null);
    }

    private void notifyRooms(int channelId, Integer roomId) {
        var failed = new ArrayList<ChannelPresenceOutput>();

        synchronized (this) {
            for (var member : List.copyOf(members.values())) {
                UUID id = member.participant.sessionId();

                if (member.channelId != channelId || member.phase != ChannelPresencePhase.LOBBY
                    || !sessions.hasSelectedCharacter(id)) {
                    continue;
                }

                var output = member.output;

                try {
                    if (roomId == null) {
                        output.roomsReplaced();
                    } else {
                        output.roomChanged(roomId);
                    }
                } catch (RuntimeException failure) {
                    LOG.failure(
                        ChannelLogEvent.LOBBY_NOTIFICATION_FAILED,
                        failure,
                        channelId(channelId),
                        recipientSessionId(id)
                    );
                    failed.add(output);
                }
            }
        }

        failed.forEach(output -> output.close("LOBBY_NOTIFICATION_FAILED"));
    }

    public synchronized void release(UUID sessionId, String closeReason) {
        var member = members.remove(sessionId);
        unsubscribe(member);

        if (member != null) {
            LOG.log(
                ChannelLogEvent.MEMBERSHIP_RELEASED,
                sessionId(sessionId),
                characterId(member.participant.characterId().value()),
                channelId(member.channelId),
                reason(closeReason)
            );
        }
    }

    private void releaseStaleCharacter(ChannelParticipant participant) {
        var previous = members.values().stream().filter(
            member -> member.participant.characterId().equals(participant.characterId())
                && !member.participant.sessionId().equals(participant.sessionId())
        )
            .map(member -> member.participant.sessionId()).toList();

        for (UUID id : previous) {
            if (sessions.hasSelectedCharacter(id)) {
                throw new IllegalStateException("Character already has an active channel membership");
            }

            release(id, "STALE_SESSION");
        }
    }

    private int population(int channelId) {
        return (int) members.values().stream().filter(member -> member.channelId == channelId).count();
    }

    private int fill(ChannelDefinition channel) {
        return (int) Math.min(100L, 100L * population(channel.id()) / channel.capacity());
    }

    private void unsubscribe(ChannelMembership member) {
        if (member != null && member.phase == ChannelPresencePhase.LOBBY) {
            member.phase = ChannelPresencePhase.MEMBER;
            member.output = null;
            publish(member, List.of(), List.of(member.participant.characterId()));
        }
    }

    private void publish(ChannelMembership source, List<ChannelParticipant> added, List<CharacterId> removed) {
        var recipients = members.values().stream().filter(
            member -> member.channelId == source.channelId
                && member.phase == ChannelPresencePhase.LOBBY
                && !member.participant.sessionId().equals(source.participant.sessionId())
        ).toList();

        for (var member : recipients) {
            member.output.update(added, removed);
        }
    }
}
