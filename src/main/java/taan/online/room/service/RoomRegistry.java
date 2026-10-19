package taan.online.room.service;

import static taan.online.room.service.RoomControlChecks.membershipCurrent;
import static taan.online.room.service.RoomControlChecks.participant;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import taan.online.channel.service.ChannelRegistry;
import taan.online.channel.service.model.ChannelMembershipSnapshot;
import taan.online.inventory.model.CharacterInventory;
import taan.online.inventory.service.model.InventoryActor;
import taan.online.inventory.service.model.InventoryMoveContext;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.Room;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomIdPool;
import taan.online.room.model.RoomMapCatalog;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomPassword;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomState;
import taan.online.room.model.RoomTeam;
import taan.online.room.service.model.ChangeRoomSettingsOutcome;
import taan.online.room.service.model.ChangeRoomSettingsResult;
import taan.online.room.service.model.ChangeRoomTeamResult;
import taan.online.room.service.model.ChangeRoomWeaponResult;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.room.service.model.CreateRoomResult;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.service.model.EnterRoomResult;
import taan.online.room.service.model.ExpelRoomParticipantResult;
import taan.online.room.service.model.LeaveRoomResult;
import taan.online.room.service.model.RoomEntryTarget;
import taan.online.room.service.model.RoomLeaveReceipt;
import taan.online.room.service.model.RoomListing;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchReservationResult;
import taan.online.room.service.model.RoomMatchTerminationResult;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;
import taan.online.room.service.model.RoomRegistryPhase;
import taan.online.room.service.model.RoomSelectionToken;
import taan.online.room.service.model.RoomSnapshot;
import taan.online.room.service.model.RoomStartResult;
import taan.online.room.service.model.SetCaptainCandidateResult;
import taan.online.room.service.model.SetRoomReadyResult;
import taan.online.room.service.model.SlotAvailabilityResult;

public final class RoomRegistry implements RoomListingAccess, AutoCloseable {
    private final ChannelRegistry channels;
    private final RoomIdPool ids;
    private final RoomMapCatalog maps;
    private final RoomStartOperations starts;
    private final RoomMatchOperations matches;
    private final RoomMatchCoordinator matchCoordinator;
    private final RoomDepartureOperations departures;
    private final Map<RoomId, Room> rooms = new HashMap<>();
    private final Map<UUID, RoomSubscription> memberships = new HashMap<>();
    private final Map<UUID, RoomLeaveReceipt> receipts = new HashMap<>();
    private volatile Map<Integer, List<RoomListing>> listings = Map.of();
    private RoomRegistryPhase phase = RoomRegistryPhase.OPEN;

    public RoomRegistry(ChannelRegistry channels, RoomMapCatalog maps, RoomSelectionScheduler scheduler) {
        this(channels, maps, scheduler, null);
    }

    public RoomRegistry(
        ChannelRegistry channels,
        RoomIdPool ids,
        RoomMapCatalog maps,
        RoomSelectionScheduler scheduler,
        LongSupplier clock,
        RandomGenerator random
    ) {
        this(channels, ids, maps, scheduler, clock, random, null);
    }

    public RoomRegistry(
        ChannelRegistry channels,
        RoomMapCatalog maps,
        RoomSelectionScheduler scheduler,
        RoomMatchLifecycle lifecycle
    ) {
        this(channels, new RoomIdPool(), maps, scheduler, System::nanoTime, RandomGenerator.getDefault(), lifecycle);
    }

    public RoomRegistry(
        ChannelRegistry channels,
        RoomIdPool ids,
        RoomMapCatalog maps,
        RoomSelectionScheduler scheduler,
        LongSupplier clock,
        RandomGenerator random,
        RoomMatchLifecycle lifecycle
    ) {
        this.channels = channels;
        this.ids = ids;
        this.maps = Objects.requireNonNull(maps);
        starts = new RoomStartOperations(
            channels,
            rooms,
            memberships,
            scheduler,
            clock,
            random,
            this::changed,
            new AdvanceCaptainSelectionAction(this)
        );
        matches = new RoomMatchOperations(this, channels, rooms, memberships, starts, lifecycle, this::changed);
        matchCoordinator = new RoomMatchCoordinator(this, matches, starts, () -> phase, this::controlRejection);
        departures = new RoomDepartureOperations(
            channels,
            ids,
            rooms,
            memberships,
            receipts,
            starts,
            matches,
            this::publish
        );
    }

    public RoomMatchCallbacks matchCallbacks() {
        return matchCoordinator;
    }

    public synchronized RoomMatchReservationResult reserveMatch(UUID id) {
        return matches.reserve(id, controlRejection(id));
    }

    public synchronized CompletionStage<RoomMatchReservationResult> commitMatch(RoomMatchToken token) {
        return matches.commit(token);
    }

    public synchronized RoomMatchReservationResult rollbackMatch(RoomMatchToken token, String reason) {
        return matches.rollback(token, reason);
    }

    public synchronized RoomMatchContext matchContext(UUID id) {
        return matches.context(id, phase);
    }

    public synchronized RoomMatchTerminationResult terminateMatch(RoomMatchContext context, RoomMatchEndReason reason) {
        return matches.terminate(context, reason, phase);
    }

    public synchronized RoomMatchTerminationResult terminateMatch(RoomMatchToken token, RoomMatchEndReason reason) {
        return matches.terminate(token, reason, null);
    }

    public synchronized RoomStartResult beginCaptainSelection(UUID id) {
        return starts.begin(id, controlRejection(id));
    }

    public synchronized RoomStartResult requestStart(UUID id) {
        return matchCoordinator.start(id);
    }

    public synchronized SetCaptainCandidateResult setCaptainCandidate(UUID id, long characterId, boolean candidate) {
        return starts.candidate(id, characterId, candidate, controlRejection(id));
    }

    public synchronized void advanceCaptainSelection(RoomSelectionToken token) {
        try {
            starts.advance(token);
        } catch (RuntimeException failure) {
            starts.failed(token, failure);
        }
    }

    @Override
    public List<RoomListing> forChannel(int channelId) {
        return listings.getOrDefault(channelId, List.of());
    }

    public synchronized Optional<RoomSnapshot> current(UUID id) {
        return membership(id).map(member -> RoomSnapshot.from(rooms.get(member.roomId())));
    }

    public synchronized Optional<RoomMembership> membership(UUID id) {
        return Optional.ofNullable(memberships.get(id)).map(RoomSubscription::membership);
    }

    public synchronized Optional<RoomEntryTarget> entryTarget(ChannelMembershipSnapshot context, int requestedId) {
        return RoomEntryTarget.find(rooms.values(), context.channelId(), requestedId);
    }

    public synchronized InventoryMoveContext inventoryContext(InventoryActor actor) {
        return matches.inventoryContext(actor, phase);
    }

    public synchronized void refreshEquipment(InventoryMoveContext context, CharacterInventory equipment) {
        if (!context.equipmentChangesAllowed() || context.roomAdmissionId() == null
            || !context.equals(inventoryContext(context.actor()))) {
            return;
        }

        RoomControlOperations.refreshEquipment(
            departures.room(context.actor().sessionId()),
            memberships.get(context.actor().sessionId()),
            List.copyOf(memberships.values()),
            equipment
        );
    }

    public synchronized CreateRoomResult create(
        ChannelMembershipSnapshot context,
        RoomSettings settings,
        RoomPassword password,
        RoomParticipant owner,
        RoomPresenceOutput output
    ) {
        if (phase == RoomRegistryPhase.CLOSED) {
            return new CreateRoomResult(CreateRoomOutcome.IGNORED, null, "SERVER_STOPPING");
        }

        UUID id = owner.sessionId();

        if (memberships.containsKey(id) || RoomControlChecks.pending(receipts.get(id))) {
            return CreateRoomResult.rejected("ALREADY_IN_ROOM");
        }

        if (!membershipCurrent(channels, context, owner)) {
            return CreateRoomResult.rejected("CHANNEL_CHANGED");
        }

        if (!RoomControlChecks.matches(context, owner)) {
            return CreateRoomResult.rejected("CHARACTER_CHANGED");
        }

        if (!maps.contains(settings.mapId())) {
            return CreateRoomResult.rejected("UNSUPPORTED_MAP");
        }

        var ownerCharacter = owner.character().id();
        var stale = memberships.values().stream()
            .filter(value -> participant(rooms, value).character().id().equals(ownerCharacter)).toList();

        for (var previous : stale) {
            release(previous.membership(), "STALE_SESSION");
        }

        var allocated = ids.allocate();

        if (allocated.isEmpty()) {
            return CreateRoomResult.rejected("ROOM_IDS_EXHAUSTED");
        }

        var room = new Room(allocated.orElseThrow(), UUID.randomUUID(), context.channelId(), settings, password, owner);
        rooms.put(room.id(), room);
        var subscription = subscribe(room, owner, output);
        var result = RoomSnapshot.from(room);
        var delivery = RoomNotifications.deliver(subscription, () -> output.initialize(result));
        publish(room.channelId());
        channels.hideFromLobby(id);
        channels.roomChanged(room.channelId(), room.id().value());
        RoomNotifications.log(RoomLogEvent.CREATION_SUCCEEDED, result, owner, "CREATED");
        return new CreateRoomResult(CreateRoomOutcome.CREATED, result, "CREATED", delivery);
    }

    public synchronized EnterRoomResult enter(
        ChannelMembershipSnapshot context,
        RoomEntryTarget target,
        RoomParticipant participant,
        String password,
        RoomPresenceOutput output
    ) {
        if (phase == RoomRegistryPhase.CLOSED) {
            return EnterRoomResult.rejected(EnterRoomOutcome.IGNORED, "SERVER_STOPPING");
        }

        if (memberships.containsKey(participant.sessionId())
            || RoomControlChecks.pending(receipts.get(participant.sessionId()))) {
            return EnterRoomResult.rejected(EnterRoomOutcome.ALREADY_IN_ROOM, "ALREADY_IN_ROOM");
        }

        if (!membershipCurrent(channels, context, participant) || !RoomControlChecks.matches(context, participant)) {
            return EnterRoomResult.rejected(EnterRoomOutcome.REJECTED, "CONTEXT_CHANGED");
        }

        var room = rooms.get(target.id());
        starts.advance(room);

        if (room == null || !room.generation().equals(target.generation()) || room.channelId() != context.channelId()
            || target.channelId() != context.channelId() || room.state() != RoomState.WAITING) {
            return EnterRoomResult.rejected(EnterRoomOutcome.NOT_FOUND, "ROOM_UNAVAILABLE");
        }

        if (room.contains(participant)) {
            return EnterRoomResult.rejected(EnterRoomOutcome.ALREADY_IN_ROOM, "ALREADY_IN_ROOM");
        }

        if (room.playerCount() >= room.settings().capacity()) {
            return EnterRoomResult.rejected(EnterRoomOutcome.FULL, "ROOM_FULL");
        }

        if (!room.acceptsPassword(password)) {
            return EnterRoomResult.rejected(EnterRoomOutcome.WRONG_PASSWORD, "WRONG_PASSWORD");
        }

        var occupied = room.enter(participant, password);

        if (occupied.isEmpty()) {
            return EnterRoomResult.rejected(EnterRoomOutcome.NO_SLOT, "NO_OPEN_SLOT");
        }

        var subscription = subscribe(room, participant, output);
        var result = RoomSnapshot.from(room);
        var delivery = RoomNotifications.deliver(subscription, () -> output.initialize(result));
        var recipients = List.copyOf(memberships.values());
        int index = occupied.orElseThrow().index();
        RoomNotifications.update(recipients, result, participant.sessionId(), index, false);
        publish(room.channelId());
        channels.hideFromLobby(participant.sessionId());
        channels.roomChanged(room.channelId(), room.id().value());
        RoomNotifications.log(RoomLogEvent.ENTRY_SUCCEEDED, result, participant, "ENTERED");
        return new EnterRoomResult(EnterRoomOutcome.ENTERED, "ENTERED", delivery);
    }

    public synchronized EnterRoomResult quickJoin(
        ChannelMembershipSnapshot context,
        RoomParticipant participant,
        RoomPresenceOutput output
    ) {
        if (phase == RoomRegistryPhase.CLOSED) {
            return EnterRoomResult.rejected(EnterRoomOutcome.IGNORED, "SERVER_STOPPING");
        }

        if (memberships.containsKey(participant.sessionId())
            || RoomControlChecks.pending(receipts.get(participant.sessionId()))) {
            return EnterRoomResult.rejected(EnterRoomOutcome.ALREADY_IN_ROOM, "ALREADY_IN_ROOM");
        }

        if (!membershipCurrent(channels, context, participant) || !RoomControlChecks.matches(context, participant)) {
            return EnterRoomResult.rejected(EnterRoomOutcome.REJECTED, "CONTEXT_CHANGED");
        }

        rooms.values().stream().filter(room -> room.channelId() == context.channelId()).toList()
            .forEach(starts::advance);
        var target = RoomEntryTarget.quickJoin(rooms.values(), context.channelId(), participant);

        if (target.isEmpty()) {
            return EnterRoomResult.rejected(EnterRoomOutcome.NOT_FOUND, "NO_MATCHING_ROOM");
        }

        return enter(context, target.orElseThrow(), participant, "", output);
    }

    public synchronized SlotAvailabilityResult setSlotAvailability(UUID id, int index, RoomSlotState desiredState) {
        return RoomControlOperations.setSlotAvailability(
            controlRejection(id),
            departures.room(id),
            memberships.get(id),
            List.copyOf(memberships.values()),
            index,
            desiredState
        );
    }

    public synchronized ChangeRoomTeamResult changeTeam(UUID id, RoomTeam desiredTeam) {
        return RoomControlOperations.changeTeam(
            controlRejection(id),
            departures.room(id),
            memberships.get(id),
            List.copyOf(memberships.values()),
            desiredTeam
        );
    }

    public synchronized SetRoomReadyResult setReady(UUID id, boolean ready) {
        return RoomControlOperations.setReady(
            controlRejection(id),
            departures.room(id),
            memberships.get(id),
            List.copyOf(memberships.values()),
            ready
        );
    }

    public synchronized ChangeRoomWeaponResult changeWeapon(UUID id, long characterId, int weaponId) {
        return RoomControlOperations.changeWeapon(
            controlRejection(id),
            departures.room(id),
            memberships.get(id),
            List.copyOf(memberships.values()),
            characterId,
            weaponId
        );
    }

    public synchronized ChangeRoomSettingsResult changeSettings(UUID id, int mapId, int gameType, int rule) {
        return updateSettings(id, mapId, gameType, rule);
    }

    public synchronized ChangeRoomSettingsResult changeMap(UUID id, int mapId) {
        return updateSettings(id, mapId, null, null);
    }

    private ChangeRoomSettingsResult updateSettings(UUID id, int mapId, Integer gameType, Integer rule) {
        String rejection = controlRejection(id);

        if (rejection != null) {
            return new ChangeRoomSettingsResult(ChangeRoomSettingsOutcome.IGNORED, rejection, null);
        }

        var subscription = memberships.get(id);
        var room = rooms.get(subscription.membership().roomId());
        return RoomControlOperations.publishSettings(
            room,
            id,
            maps,
            mapId,
            gameType,
            rule,
            List.copyOf(memberships.values()),
            subscription,
            this::changed
        );
    }

    public synchronized ExpelRoomParticipantResult expel(UUID id, long targetCharacterId) {
        return RoomExpulsionOperations.expel(
            id,
            targetCharacterId,
            rooms,
            memberships,
            receipts,
            this::controlRejection,
            this::remove,
            room -> starts.cancel(room, "EXPELLED")
        );
    }

    public synchronized LeaveRoomResult leave(UUID id, int requestedRoomId, int requestedReason) {
        return departures.leave(id, requestedRoomId, requestedReason);
    }

    public synchronized void release(UUID id, String closeReason) {
        membership(id).ifPresent(member -> remove(member, closeReason));
        receipts.remove(id);
    }

    public synchronized void release(RoomMembership expected, String closeReason) {
        remove(expected, closeReason);
    }

    @Override
    public synchronized void close() {
        phase = RoomRegistryPhase.CLOSED;

        for (var value : List.copyOf(memberships.values())) {
            remove(value.membership(), "SERVER_STOPPING");
        }

        receipts.clear();
    }

    private RoomSubscription subscribe(Room room, RoomParticipant participant, RoomPresenceOutput output) {
        var subscription = RoomSubscription.create(room, participant, output);
        memberships.put(participant.sessionId(), subscription);
        receipts.remove(participant.sessionId());
        return subscription;
    }

    private String controlRejection(UUID id) {
        starts.advance(departures.room(id));
        var subscription = memberships.get(id);
        return RoomControlChecks.rejection(
            phase,
            subscription == null ? null : subscription.membership(),
            channels.membership(id).orElse(null),
            subscription == null ? null : participant(rooms, subscription)
        );
    }

    private void remove(RoomMembership expected, String closeReason) {
        departures.remove(expected, closeReason);
    }

    private void changed(Room room) {
        publish(room.channelId());
        channels.roomChanged(room.channelId(), room.id().value());
    }

    private void publish(int channelId) {
        listings = RoomListing.replaceChannel(listings, rooms.values(), channelId);
    }
}
