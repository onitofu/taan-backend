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
import taan.online.protocol.MalformedPacketException;
import taan.online.protocol.connection.packet.PqFirstPacket;
import taan.online.protocol.connection.packet.PqKeepAlive;
import taan.online.room.protocol.packet.PqGameSessionChangeTeam;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.protocol.packet.PqGameSessionLeave;
import taan.online.room.protocol.packet.PqGameSessionOpenCloseSlot;
import taan.online.room.protocol.packet.PqGameSessionQuickJoin;
import taan.online.room.protocol.packet.PqGameSessionReady;
import taan.online.room.protocol.packet.PqGameSessionStartGame;
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
            case PqGameSessionChangeTeam.TYPE_ID -> ProtocolEvent.ROOM_CHANGE_TEAM;
            case PqGameSessionEnter.TYPE_ID -> ProtocolEvent.ROOM_ENTER;
            case PqGameSessionQuickJoin.TYPE_ID -> ProtocolEvent.ROOM_QUICK_JOIN;
            case PqGameSessionOpenCloseSlot.TYPE_ID -> ProtocolEvent.ROOM_SLOT_AVAILABILITY;
            case PqGameSessionLeave.TYPE_ID -> ProtocolEvent.ROOM_LEAVE;
            case PqGameSessionStartGame.TYPE_ID -> ProtocolEvent.ROOM_START;
            case PqGameSessionReady.TYPE_ID -> ProtocolEvent.ROOM_READY;
            case PqKeepAlive.TYPE_ID -> ProtocolEvent.KEEP_ALIVE;
            default -> throw new MalformedPacketException("UNSUPPORTED_PACKET");
        };
    }

}
