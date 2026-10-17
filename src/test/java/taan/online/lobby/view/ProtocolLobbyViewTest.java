package taan.online.lobby.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.channel.model.ChannelParticipant;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;

class ProtocolLobbyViewTest {
    @Test
    void rendersBothGenderIconsInInitialAndIncrementalRosters() {
        var output = new CollectingPacketOutput();
        var view = new ProtocolLobbyView(output);
        var male = new ChannelParticipant(
            UUID.randomUUID(),
            new AccountId(1),
            new CharacterId(1),
            new CharacterName("Male"),
            1,
            AccountGender.MALE
        );
        var female = new ChannelParticipant(
            UUID.randomUUID(),
            new AccountId(2),
            new CharacterId(2),
            new CharacterName("Luna"),
            2,
            AccountGender.FEMALE
        );

        view.initialize(2, List.of(male, female)).toCompletableFuture().join();

        assertEquals(3, output.batches.getFirst().size());
        assertArrayEquals(
            HexFormat.of().parseHex(
                "d9071d5002000000"
                    + "9505b02901000000040000004d0061006c006500010001"
                    + "9505b02902000000040000004c0075006e006100020002"
                    + "00000000"
            ),
            output.batches.getFirst().get(2)
        );

        view.update(List.of(male, female), List.of()).toCompletableFuture().join();

        assertEquals(2, output.batches.size());
        assertEquals(1, output.batches.getLast().size());
        assertArrayEquals(
            HexFormat.of().parseHex(
                "a808236102000000"
                    + "9505b02901000000040000004d0061006c006500010001"
                    + "9505b02902000000040000004c0075006e006100020002"
                    + "0000000000000000"
            ),
            output.batches.getLast().getFirst()
        );
        assertThrows(
            NullPointerException.class,
            () -> new ChannelParticipant(
                male.sessionId(),
                male.accountId(),
                male.characterId(),
                male.name(),
                male.level(),
                null
            )
        );
    }
}
