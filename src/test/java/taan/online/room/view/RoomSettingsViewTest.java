package taan.online.room.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.root;

import org.junit.jupiter.api.Test;
import taan.online.CollectingPacketOutput;
import taan.online.RoomScenario;
import taan.online.protocol.connection.codec.NoticeMessageCodec;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomSettings;

class RoomSettingsViewTest {
    @Test
    void normalizesTheIgnoredByteAndPlacesAuthoritativeResyncBeforeTheRejectionNotice() throws Exception {
        var output = new CollectingPacketOutput();
        var view = new ProtocolRoomView(output);
        var defaults = RoomScenario.settings();
        var selected = new RoomSettings(defaults.title(), RoomMode.NO_ITEM, RoomRule.BASIC, 2, 8);
        view.settingsChanged(selected, false);

        assertEquals(1, output.batches.getFirst().size());
        assertArrayEquals(integer(root("04-ChangeGameOption.reply"), 9, 255, 1), output.batches.getFirst().getFirst());
        view.settingsChanged(defaults, true);
        var rejected = output.batches.getLast();

        assertEquals(2, rejected.size());
        assertArrayEquals(integer(root("PcChangeGameOption-rejected-current"), 9, 255, 1), rejected.getFirst());
        assertEquals(
            "Requested room settings are not allowed.",
            new NoticeMessageCodec().decode(rejected.get(1)).message()
        );
    }
}
