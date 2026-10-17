package taan.online.server;

import taan.online.protocol.connection.packet.PqFirstPacket;

import taan.online.account.protocol.packet.PqCheckAccount;
import taan.online.account.protocol.packet.PqCreateAccount;
import taan.online.character.protocol.packet.PqConfirmName;
import taan.online.character.protocol.packet.PqNewCharacter;
import taan.online.character.protocol.packet.PqDelCharacter;
import taan.online.character.protocol.packet.PqSelectCharacter;
import taan.online.channel.protocol.packet.PqGetChannelList;
import taan.online.session.protocol.packet.PcUpdateCash;
import taan.online.session.protocol.packet.PcLogging;
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
            default -> ProtocolEvent.KEEP_ALIVE;
        };
    }

}
