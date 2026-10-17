package taan.online.server;

import java.util.UUID;
import taan.online.character.controller.ConfirmNamePacketHandler;
import taan.online.character.controller.CreateCharacterPacketHandler;
import taan.online.character.logging.CharacterLogEvent;
import taan.online.character.protocol.codec.ConfirmNameResponseCodec;
import taan.online.character.protocol.codec.CreateCharacterResponseCodec;
import taan.online.character.protocol.packet.PqConfirmName;
import taan.online.character.protocol.packet.PqNewCharacter;

final class CharacterRequestDispatcher {
    private final ConfirmNamePacketHandler confirm;
    private final CreateCharacterPacketHandler create;

    CharacterRequestDispatcher(ConfirmNamePacketHandler confirm, CreateCharacterPacketHandler create) {
        this.confirm = confirm;
        this.create = create;
    }

    RequestCompletion dispatch(int type, UUID sessionId, byte[] root) {
        if (type == PqConfirmName.TYPE_ID) {
            return confirm.handle(sessionId, root).map(
                reply -> new RequestCompletion(
                    ProtocolEvent.NAME_CHECK_COMPLETED,
                    new ConfirmNameResponseCodec().encode(reply)
                )
            ).orElseGet(() -> new RequestCompletion(ProtocolEvent.CHARACTER_REQUEST_IGNORED, null));
        }

        return create.handle(sessionId, root).map(
            reply -> new RequestCompletion(
                reply.result() == 0 ? ProtocolEvent.CHARACTER_CREATED : ProtocolEvent.CHARACTER_REJECTED,
                new CreateCharacterResponseCodec().encode(reply)
            )
        ).orElseGet(() -> new RequestCompletion(ProtocolEvent.CHARACTER_REQUEST_IGNORED, null));
    }

    static boolean accepts(int type) {
        return type == PqConfirmName.TYPE_ID || type == PqNewCharacter.TYPE_ID;
    }

    static CharacterLogEvent failureEvent(int type) {
        return type == PqConfirmName.TYPE_ID ? CharacterLogEvent.NAME_CHECK_FAILED : CharacterLogEvent.CREATION_FAILED;
    }
}
