package taan.online.server;

import java.util.concurrent.ThreadPoolExecutor;
import taan.online.account.protocol.packet.PqCheckAccount;
import taan.online.account.protocol.packet.PqCreateAccount;
import taan.online.character.protocol.packet.PqSelectCharacter;
import taan.online.room.protocol.packet.PqGameSessionCreate;
import taan.online.room.protocol.packet.PqGameSessionEnter;
import taan.online.room.protocol.packet.PqGameSessionQuickJoin;
import taan.online.session.protocol.packet.PcUpdateCash;
import taan.online.session.protocol.packet.PqUserLogin;

public record RequestExecutors(
    ThreadPoolExecutor authentication,
    ThreadPoolExecutor database,
    ThreadPoolExecutor requests
) {
    ThreadPoolExecutor forType(int type) {
        return switch (classification(type)) {
            case AUTHENTICATION -> authentication;
            case DATABASE -> database;
            case REQUEST -> requests;
        };
    }

    static RequestExecutionClass classification(int type) {
        RequestEvents.forType(type);

        return switch (type) {
            case PqUserLogin.TYPE_ID, PqCreateAccount.TYPE_ID -> RequestExecutionClass.AUTHENTICATION;
            case PqCheckAccount.TYPE_ID, PqSelectCharacter.TYPE_ID, PcUpdateCash.TYPE_ID,
                PqGameSessionCreate.TYPE_ID, PqGameSessionEnter.TYPE_ID, PqGameSessionQuickJoin.TYPE_ID ->
                RequestExecutionClass.DATABASE;
            default -> CharacterRequestDispatcher.accepts(type) || InventoryRequestDispatcher.accepts(type)
                ? RequestExecutionClass.DATABASE : RequestExecutionClass.REQUEST;
        };
    }
}
