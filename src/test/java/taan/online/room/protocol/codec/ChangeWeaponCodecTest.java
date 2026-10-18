package taan.online.room.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static taan.online.RoomFixtures.integer;
import static taan.online.RoomFixtures.malformed;
import static taan.online.RoomFixtures.root;

import org.junit.jupiter.api.Test;
import taan.online.RoomScenario;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.PcGameSessionChangeWeapon;

class ChangeWeaponCodecTest {
    @Test
    void matchesTheIndependentNativeGuestCaptureAndUsesCharacterIdentity() throws Exception {
        var request = root("03-ChangeWeapon.request");
        var reply = root("03-ChangeWeapon.reply");
        var requests = new RoomRequestCodec();
        var responses = new RoomResponseCodec();
        var decoded = requests.changeWeapon(request);

        assertEquals(1002, decoded.characterId());
        assertEquals(0, decoded.weaponId());
        assertArrayEquals(RoomScenario.weapon(1002, 0), request);
        assertEquals(new PcGameSessionChangeWeapon(1002, 0), responses.changeWeapon(reply));
        assertArrayEquals(reply, responses.encode(new PcGameSessionChangeWeapon(1002, 0)));
        malformed(requests::changeWeapon, request);
        malformed(responses::changeWeapon, reply);
    }

    @Test
    void keepsWireRangesSeparateFromAllowedRoomChoices() throws Exception {
        var codec = new RoomResponseCodec();

        for (int weapon : new int[]{0, 11, 100, 200, 300, 65535}) {
            var expected = new PcGameSessionChangeWeapon(0xffff_ffffL, weapon);
            assertEquals(expected, codec.changeWeapon(codec.encode(expected)));
            var request = new RoomRequestCodec().changeWeapon(RoomScenario.weapon(0xffff_ffffL, weapon));
            assertEquals(weapon, request.weaponId());
            assertEquals(0xffff_ffffL, request.characterId());
        }

        for (long id : new long[]{-1, 0, 0x1_0000_0000L}) {
            assertThrows(MalformedPacketException.class, () -> codec.encode(new PcGameSessionChangeWeapon(id, 0)));
        }

        assertThrows(IllegalArgumentException.class, () -> codec.encode(new PcGameSessionChangeWeapon(1, -1)));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new PcGameSessionChangeWeapon(1, 65536)));
        assertThrows(
            MalformedPacketException.class,
            () -> codec.changeWeapon(integer(root("03-ChangeWeapon.reply"), 4, 0, 4))
        );
        assertEquals(0, new RoomRequestCodec().changeWeapon(RoomScenario.weapon(0, 0)).characterId());
    }
}
