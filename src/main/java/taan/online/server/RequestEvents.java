package taan.online.server;

import taan.online.account.protocol.packet.PqCheckAccount;
import taan.online.account.protocol.packet.PqCreateAccount;
import taan.online.channel.protocol.packet.PqChannelMoveTo;
import taan.online.channel.protocol.packet.PqGetChannelList;
import taan.online.character.protocol.packet.PqConfirmName;
import taan.online.character.protocol.packet.PqDelCharacter;
import taan.online.character.protocol.packet.PqNewCharacter;
import taan.online.character.protocol.packet.PqSelectCharacter;
import taan.online.inventory.protocol.packet.PqMoveInventoryItem;
import taan.online.lobby.protocol.packet.PqLobbyEnter;
import taan.online.lobby.protocol.packet.PqLobbyLeave;
import taan.online.match.protocol.packet.PcGamePlayGiveUp;
import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.protocol.packet.PcGamePlayLoadingDone;
import taan.online.match.protocol.packet.PcGamePlaySetPos;
import taan.online.match.protocol.packet.PcGamePlayMove;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.match.protocol.packet.PqGameSessionReportEnemies;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.UnsupportedGameplayPacket;
import taan.online.match.protocol.packet.PqResultRandSelect;
import taan.online.match.protocol.packet.PqResultSelectCard;
import taan.online.protocol.MalformedPacketException;
import taan.online.protocol.connection.packet.PqFirstPacket;
import taan.online.protocol.connection.packet.PqKeepAlive;
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
import taan.online.room.protocol.packet.PqGameSessionStartGame;
import taan.online.room.protocol.packet.PqGameSessionUserExpel;
import taan.online.session.protocol.packet.PcLogging;
import taan.online.session.protocol.packet.PcUpdateCash;
import taan.online.session.protocol.packet.PqUserLogin;

final class RequestEvents {
    private RequestEvents() {
    }

    static ProtocolEvent forType(int type) {
        return switch (type) {
            case PqCheckAccount.TYPE_ID -> ProtocolEvent.CHECK_ACCOUNT;
            case PqCreateAccount.TYPE_ID -> ProtocolEvent.CREATE_ACCOUNT;
            case PqFirstPacket.TYPE_ID -> ProtocolEvent.FIRST_PACKET;
            case PqUserLogin.TYPE_ID -> ProtocolEvent.USER_LOGIN;
            case PqConfirmName.TYPE_ID -> ProtocolEvent.CONFIRM_NAME;
            case PqNewCharacter.TYPE_ID -> ProtocolEvent.NEW_CHARACTER;
            case PqDelCharacter.TYPE_ID -> ProtocolEvent.DELETE_CHARACTER;
            case PqSelectCharacter.TYPE_ID -> ProtocolEvent.SELECT_CHARACTER;
            case PqGetChannelList.TYPE_ID -> ProtocolEvent.GET_CHANNEL_LIST;
            case PcUpdateCash.TYPE_ID -> ProtocolEvent.UPDATE_CASH;
            case PcLogging.TYPE_ID -> ProtocolEvent.CLIENT_LOGGING;
            case PqMoveInventoryItem.TYPE_ID -> ProtocolEvent.MOVE_INVENTORY_ITEM;
            case PqChannelMoveTo.TYPE_ID -> ProtocolEvent.MOVE_TO;
            case PqLobbyEnter.TYPE_ID -> ProtocolEvent.LOBBY_ENTER;
            case PqLobbyLeave.TYPE_ID -> ProtocolEvent.LOBBY_LEAVE;
            case PqGameSessionCreate.TYPE_ID -> ProtocolEvent.ROOM_CREATE;
            case PqGameSessionUserExpel.TYPE_ID -> ProtocolEvent.ROOM_EXPEL;
            case PqGameSessionChangeMap.TYPE_ID, PqGameSessionChangeGameOption.TYPE_ID -> ProtocolEvent.ROOM_SETTINGS;
            case PqGameSessionChangeWeapon.TYPE_ID -> ProtocolEvent.ROOM_CHANGE_WEAPON;
            case PqGameSessionChangeTeam.TYPE_ID -> ProtocolEvent.ROOM_CHANGE_TEAM;
            case PqGameSessionEnter.TYPE_ID -> ProtocolEvent.ROOM_ENTER;
            case PqGameSessionQuickJoin.TYPE_ID -> ProtocolEvent.ROOM_QUICK_JOIN;
            case PqGameSessionOpenCloseSlot.TYPE_ID -> ProtocolEvent.ROOM_SLOT_AVAILABILITY;
            case PqGameSessionLeave.TYPE_ID -> ProtocolEvent.ROOM_LEAVE;
            case PqGameSessionStartGame.TYPE_ID -> ProtocolEvent.ROOM_START;
            case PqGameSessionChiefDecision.TYPE_ID -> ProtocolEvent.ROOM_CHIEF_DECISION;
            case PqGameSessionChallengeChief.TYPE_ID, PqGameSessionCancelChief.TYPE_ID ->
                ProtocolEvent.ROOM_CAPTAIN_CANDIDATE;
            case PqGameSessionReady.TYPE_ID -> ProtocolEvent.ROOM_READY;
            case PcGamePlayEatBonusItem.TYPE_ID, PcGamePlayLoadingDone.TYPE_ID, PcGamePlaySetPos.TYPE_ID,
                PqGameSessionReportEnemies.TYPE_ID,
                PcGamePlayEndScenario.TYPE_ID, PcGamePlayMove.TYPE_ID,
                PcGamePlayPassTurn.TYPE_ID, PcGamePlaySpendItem.TYPE_ID, UnsupportedGameplayPacket.USE_ITEM_TYPE_ID,
                UnsupportedGameplayPacket.SCENARIO_TYPE_ID,
                PcGamePlayGiveUp.TYPE_ID, PqResultRandSelect.TYPE_ID, PqResultSelectCard.TYPE_ID ->
                ProtocolEvent.MATCH_REQUEST;
            case PqKeepAlive.TYPE_ID -> ProtocolEvent.KEEP_ALIVE;
            default -> throw new MalformedPacketException("UNSUPPORTED_PACKET");
        };
    }

}
