package taan.online.server;

import java.util.UUID;
import taan.online.character.controller.ConfirmNamePacketHandler;
import taan.online.character.controller.CreateCharacterPacketHandler;
import taan.online.character.controller.DeleteCharacterPacketHandler;
import taan.online.character.logging.CharacterLogEvent;
import taan.online.character.protocol.codec.ConfirmNameResponseCodec;
import taan.online.character.protocol.codec.CreateCharacterResponseCodec;
import taan.online.character.protocol.codec.DeleteCharacterResponseCodec;
import taan.online.character.protocol.packet.PqConfirmName;
import taan.online.character.protocol.packet.PqNewCharacter;
import taan.online.character.protocol.packet.PqDelCharacter;

final class CharacterRequestDispatcher {
    private final ConfirmNamePacketHandler confirm;
    private final CreateCharacterPacketHandler create;

    private final DeleteCharacterPacketHandler delete;

    CharacterRequestDispatcher(
        ConfirmNamePacketHandler confirm,
        CreateCharacterPacketHandler create,
        DeleteCharacterPacketHandler delete
    ) {
        this.confirm = confirm;
        this.create = create;
        this.delete = delete;
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

        if (type == PqDelCharacter.TYPE_ID) {
            return delete.handle(sessionId, root).map(
                reply -> new RequestCompletion(
                    reply.result() == 0 ? ProtocolEvent.CHARACTER_DELETED : ProtocolEvent.CHARACTER_REJECTED,
                    new DeleteCharacterResponseCodec().encode(reply)
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
        return type == PqConfirmName.TYPE_ID || type == PqNewCharacter.TYPE_ID || type == PqDelCharacter.TYPE_ID;
    }

    static CharacterLogEvent failureEvent(int type) {
        return switch (type) {
            case PqConfirmName.TYPE_ID -> CharacterLogEvent.NAME_CHECK_FAILED;
            case PqDelCharacter.TYPE_ID -> CharacterLogEvent.DELETION_FAILED;
            default -> CharacterLogEvent.CREATION_FAILED;
        };
    }
}
