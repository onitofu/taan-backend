package taan.online.room.controller;

import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.sessionId;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.logging.EventLogger;
import taan.online.protocol.PacketOutput;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.protocol.connection.packet.PcNoticeMsg;
import taan.online.room.logging.RoomLogEvent;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomTeam;
import taan.online.room.protocol.codec.RoomRequestCodec;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.PqGameSessionCancelChief;
import taan.online.room.protocol.packet.PqGameSessionChallengeChief;
import taan.online.room.protocol.packet.PqGameSessionChangeGameOption;
import taan.online.room.protocol.packet.PqGameSessionChangeMap;
import taan.online.room.protocol.packet.PqGameSessionChangeTeam;
import taan.online.room.protocol.packet.PqGameSessionChangeWeapon;
import taan.online.room.protocol.packet.PqGameSessionChiefDecision;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.protocol.packet.PqGameSessionLeave;
import taan.online.room.protocol.packet.PqGameSessionOpenCloseSlot;
import taan.online.room.protocol.packet.PqGameSessionQuickJoin;
import taan.online.room.protocol.packet.PqGameSessionReady;
import taan.online.room.protocol.packet.PqGameSessionUserExpel;
import taan.online.room.protocol.packet.PrGameSessionCreateFail;
import taan.online.room.protocol.packet.PrGameSessionEnterFail;
import taan.online.room.protocol.packet.PrGameSessionLeave;
import taan.online.room.protocol.packet.PrGameSessionQuickJoinFail;
import taan.online.room.service.BeginCaptainSelectionAction;
import taan.online.room.service.ChangeRoomSettingsAction;
import taan.online.room.service.ChangeRoomTeamAction;
import taan.online.room.service.ChangeRoomWeaponAction;
import taan.online.room.service.CreateRoomAction;
import taan.online.room.service.EnterRoomAction;
import taan.online.room.service.ExpelRoomParticipantAction;
import taan.online.room.service.LeaveRoomAction;
import taan.online.room.service.QuickJoinRoomAction;
import taan.online.room.service.RequestRoomStartAction;
import taan.online.room.service.RoomRegistry;
import taan.online.room.service.SetCaptainCandidateAction;
import taan.online.room.service.SetRoomReadyAction;
import taan.online.room.service.SetRoomSlotAvailabilityAction;
import taan.online.room.service.model.ChangeRoomSettingsOutcome;
import taan.online.room.service.model.ChangeRoomSettingsResult;
import taan.online.room.service.model.ChangeRoomTeamOutcome;
import taan.online.room.service.model.ChangeRoomWeaponOutcome;
import taan.online.room.service.model.CreateRoomOutcome;
import taan.online.room.service.model.EnterRoomOutcome;
import taan.online.room.service.model.ExpelRoomParticipantOutcome;
import taan.online.room.service.model.RoomStartOutcome;
import taan.online.room.service.model.SetCaptainCandidateOutcome;
import taan.online.room.service.model.SetRoomReadyOutcome;
import taan.online.room.service.model.SlotAvailabilityOutcome;
import taan.online.room.view.ProtocolRoomView;

public final class RoomPacketHandler {
    private static final EventLogger LOG = EventLogger.forClass(RoomPacketHandler.class);

    private final SelectedCharacterAccess sessions;
    private final CreateRoomAction create;
    private final EnterRoomAction enter;
    private final QuickJoinRoomAction quickJoin;
    private final ExpelRoomParticipantAction expel;
    private final ChangeRoomSettingsAction settings;
    private final ChangeRoomTeamAction changeTeam;
    private final ChangeRoomWeaponAction changeWeapon;
    private final SetRoomReadyAction ready;
    private final SetRoomSlotAvailabilityAction slotAvailability;
    private final LeaveRoomAction leave;
    private final RequestRoomStartAction start;
    private final BeginCaptainSelectionAction chiefDecision;
    private final SetCaptainCandidateAction candidate;
    private final RoomRegistry rooms;

    public RoomPacketHandler(
        SelectedCharacterAccess sessions,
        CreateRoomAction create,
        EnterRoomAction enter,
        QuickJoinRoomAction quickJoin,
        ExpelRoomParticipantAction expel,
        ChangeRoomSettingsAction settings,
        ChangeRoomTeamAction changeTeam,
        ChangeRoomWeaponAction changeWeapon,
        SetRoomReadyAction ready,
        SetRoomSlotAvailabilityAction slotAvailability,
        LeaveRoomAction leave,
        RequestRoomStartAction start,
        BeginCaptainSelectionAction chiefDecision,
        SetCaptainCandidateAction candidate,
        RoomRegistry rooms
    ) {
        this.sessions = sessions;
        this.create = create;
        this.enter = enter;
        this.quickJoin = quickJoin;
        this.expel = expel;
        this.settings = settings;
        this.changeTeam = changeTeam;
        this.changeWeapon = changeWeapon;
        this.ready = ready;
        this.slotAvailability = slotAvailability;
        this.leave = leave;
        this.start = start;
        this.chiefDecision = chiefDecision;
        this.candidate = candidate;
        this.rooms = rooms;
    }

    public RoomReply handle(UUID id, int type, byte[] root, PacketOutput output) {
        if (!sessions.hasSelectedCharacter(id)) {
            return ignored();
        }

        var codec = new RoomRequestCodec();

        if (type == PqGameSessionChiefDecision.TYPE_ID) {
            codec.chiefDecision(root);
            var result = chiefDecision.execute(id);
            return result.outcome() == RoomStartOutcome.IGNORED || !sessions.hasSelectedCharacter(id) ? ignored()
                : new RoomReply(RoomReplyOutcome.CAPTAIN_COMPLETED, result.delivery());
        }

        if (type == PqGameSessionChallengeChief.TYPE_ID || type == PqGameSessionCancelChief.TYPE_ID) {
            boolean requested = type == PqGameSessionChallengeChief.TYPE_ID;
            long character = requested ? codec.challengeChief(root).characterId()
                : codec.cancelChief(root).characterId();
            var result = candidate.execute(id, character, requested);
            return result.outcome() == SetCaptainCandidateOutcome.IGNORED || !sessions.hasSelectedCharacter(id)
                ? ignored()
                : new RoomReply(RoomReplyOutcome.CAPTAIN_COMPLETED, result.delivery());
        }

        if (type == PqGameSessionUserExpel.TYPE_ID) {
            var result = expel.execute(id, codec.expel(root).targetCharacterId());

            if (result.outcome() == ExpelRoomParticipantOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            return new RoomReply(RoomReplyOutcome.EXPEL_COMPLETED, CompletableFuture.completedFuture(null));
        }

        if (type == PqGameSessionChangeMap.TYPE_ID || type == PqGameSessionChangeGameOption.TYPE_ID) {
            ChangeRoomSettingsResult result;

            if (type == PqGameSessionChangeMap.TYPE_ID) {
                result = settings.changeMap(id, codec.changeMap(root).mapId());
            } else {
                var request = codec.settings(root);
                result = settings.execute(
                    id,
                    RoomSettingsMapper.settings(request.mapId(), request.gameType(), request.rule())
                );
            }

            if (result.outcome() == ChangeRoomSettingsOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            var outcome = result.outcome() == ChangeRoomSettingsOutcome.REJECTED
                ? RoomReplyOutcome.SETTINGS_REJECTED : RoomReplyOutcome.SETTINGS_UPDATED;
            return new RoomReply(outcome, result.delivery());
        }

        if (type == PqGameSessionChangeWeapon.TYPE_ID) {
            var request = codec.changeWeapon(root);
            var result = changeWeapon.execute(id, request.characterId(), request.weaponId());

            if (result.outcome() == ChangeRoomWeaponOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            var outcome = result.outcome() == ChangeRoomWeaponOutcome.REJECTED
                ? RoomReplyOutcome.WEAPON_REJECTED : RoomReplyOutcome.WEAPON_UPDATED;
            return new RoomReply(outcome, result.delivery());
        }

        if (type == PqGameSessionReady.TYPE_ID) {
            int value = codec.ready(root).ready();

            if (value > 1) {
                LOG.log(RoomLogEvent.READY_REJECTED, sessionId(id), reason("INVALID_READY_VALUE"));
                return new RoomReply(RoomReplyOutcome.READY_REJECTED, CompletableFuture.completedFuture(null));
            }

            var result = ready.execute(id, value == 1);

            if (result.outcome() == SetRoomReadyOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            var outcome = result.outcome() == SetRoomReadyOutcome.REJECTED
                ? RoomReplyOutcome.READY_REJECTED : RoomReplyOutcome.READY_UPDATED;
            return new RoomReply(outcome, result.delivery());
        }

        if (type == PqGameSessionChangeTeam.TYPE_ID) {
            var request = codec.changeTeam(root);
            var team = switch (request.team()) {
                case 1 -> RoomTeam.BLUE;
                case 2 -> RoomTeam.RED;
                default -> null;
            };
            var result = changeTeam.execute(id, team);

            if (result.outcome() == ChangeRoomTeamOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            if (result.outcome() == ChangeRoomTeamOutcome.NO_OPEN_SLOT) {
                var notice = new PcNoticeMsg("No open slots on the requested team.");
                return new RoomReply(
                    RoomReplyOutcome.TEAM_REJECTED,
                    output.send(List.of(new NoticeMessageCodec().encode(notice)))
                );
            }

            var outcome = switch (result.outcome()) {
                case CHANGED -> RoomReplyOutcome.TEAM_CHANGED;
                case UNCHANGED -> RoomReplyOutcome.TEAM_UNCHANGED;
                default -> RoomReplyOutcome.TEAM_REJECTED;
            };
            return new RoomReply(outcome, result.delivery());
        }

        if (type == PqGameSessionOpenCloseSlot.TYPE_ID) {
            var request = codec.slotAvailability(root);
            var state = switch (request.selector()) {
                case 0 -> RoomSlotState.CLOSED;
                case 1 -> RoomSlotState.OPEN;
                default -> null;
            };
            var result = slotAvailability.execute(id, request.slotIndex(), state);

            if (result.outcome() == SlotAvailabilityOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            var outcome = result.outcome() == SlotAvailabilityOutcome.REJECTED
                ? RoomReplyOutcome.SLOT_AVAILABILITY_REJECTED : RoomReplyOutcome.SLOT_AVAILABILITY_UPDATED;
            return new RoomReply(outcome, result.delivery());
        }

        if (type == PqGameSessionQuickJoin.TYPE_ID) {
            var request = codec.quickJoin(root);
            var result = quickJoin.execute(id, request.selector(), new ProtocolRoomView(output));

            if (result.outcome() == EnterRoomOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            if (result.outcome() == EnterRoomOutcome.ENTERED) {
                return new RoomReply(RoomReplyOutcome.ENTERED, result.delivery());
            }

            return new RoomReply(
                RoomReplyOutcome.QUICK_JOIN_REJECTED,
                output.send(List.of(new RoomResponseCodec().encode(new PrGameSessionQuickJoinFail())))
            );
        }

        if (type == PqGameSessionEnter.TYPE_ID) {
            var request = codec.enter(root);
            var result = enter.execute(id, request.roomId(), request.password(), new ProtocolRoomView(output));

            if (result.outcome() == EnterRoomOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            if (result.outcome() == EnterRoomOutcome.ENTERED) {
                return new RoomReply(RoomReplyOutcome.ENTERED, result.delivery());
            }

            int reason = switch (result.outcome()) {
                case ALREADY_IN_ROOM -> 3;
                case NOT_FOUND -> 4;
                case FULL -> 5;
                case WRONG_PASSWORD -> 6;
                case NO_SLOT -> 8;
                default -> 0;
            };
            return new RoomReply(
                RoomReplyOutcome.ENTRY_REJECTED,
                output.send(List.of(new RoomResponseCodec().encode(new PrGameSessionEnterFail(reason))))
            );
        }

        if (type == PqGameSessionCreate.TYPE_ID) {
            var request = codec.create(root);
            var command = RoomSettingsMapper.create(request.info());
            var result = create.execute(id, command, new ProtocolRoomView(output));

            if (result.outcome() == CreateRoomOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            return result.outcome() == CreateRoomOutcome.CREATED
                ? new RoomReply(RoomReplyOutcome.CREATED, result.delivery()) : new RoomReply(
                    RoomReplyOutcome.CREATE_REJECTED,
                    output.send(List.of(new RoomResponseCodec().encode(new PrGameSessionCreateFail(0))))
                );
        }

        if (type == PqGameSessionLeave.TYPE_ID) {
            var request = codec.leave(root);
            var result = leave.execute(id, request.roomId(), request.reason());
            var outcome = switch (result.outcome()) {
                case LEFT -> RoomReplyOutcome.LEFT;
                case REPLAYED -> RoomReplyOutcome.LEAVE_REPLAYED;
                case REJECTED -> RoomReplyOutcome.LEAVE_REJECTED;
                case IGNORED -> RoomReplyOutcome.IGNORED;
            };

            if (outcome == RoomReplyOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
                return ignored();
            }

            return new RoomReply(
                outcome,
                output.send(
                    List.of(
                        new RoomResponseCodec().encode(
                            new PrGameSessionLeave(outcome == RoomReplyOutcome.LEAVE_REJECTED ? 0 : 1)
                        )
                    )
                )
            );
        }

        codec.start(root);
        var result = start.execute(id);

        if (result.outcome() == RoomStartOutcome.IGNORED || !sessions.hasSelectedCharacter(id)) {
            return ignored();
        }

        return new RoomReply(RoomReplyOutcome.START_COMPLETED, result.delivery());
    }

    public void close(UUID id, String reason) {
        rooms.release(id, reason);
    }

    private RoomReply ignored() {
        return new RoomReply(RoomReplyOutcome.IGNORED, CompletableFuture.completedFuture(null));
    }
}
