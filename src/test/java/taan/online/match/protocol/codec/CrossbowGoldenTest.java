package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.CrossbowFixtures;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioGuidedCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.protocol.MalformedPacketException;

class CrossbowGoldenTest {
    @Test
    void preservesBothNativeNormalShotsAndTheIntrinsicallyGuidedSpecialWithoutInventingAnItem() throws Exception {
        String[] names = {"a", "b", "special"};
        String[] hashes = {
            "e201314fce16b319ab6f17c84c9223563d24b8294f68b4b5d438258090f4f8e9",
            "80b69a31050177251bf3751f46758ada5295ee5de43c0c7bfaa6881962416b1c",
            "74e091a9377a5971a8f2d692a58fb2ab4c48fa958a58b0a67e26a9bcbd5e4e20"
        };
        var codec = new ScenarioCodec();

        for (int index = 0; index < names.length; index++) {
            byte[] root = CrossbowFixtures.root(names[index]);
            var packet = codec.decode(root);
            assertEquals(hashes[index], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root)));
            assertArrayEquals(root, codec.encode(packet));
            assertEquals(index == 1 ? 2 : 0, packet.shooterSlot());
            var fire = packet.scenario().commands().stream().filter(ScenarioFireCommand.class::isInstance)
                .map(ScenarioFireCommand.class::cast).findFirst().orElseThrow();
            assertEquals(3, fire.weaponId());
            assertEquals(index == 2 ? 2 : 0, fire.attackKind());
            assertEquals(index == 2 ? 4 : 0, fire.weapon().variant());
            assertEquals(index == 2 ? 3200 : 700, fire.weapon().shotTick());
            assertTrue(packet.scenario().effects().isEmpty());
            assertTrue(!packet.scenario().selectedItems().contains(7));

            for (int length = 0; length < root.length; length++) {
                byte[] truncated = Arrays.copyOf(root, length);
                assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
            }

            assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(root, root.length + 1)));
        }

        var special = codec.decode(CrossbowFixtures.root("special"));
        var prelude = assertInstanceOf(ScenarioNormalCommand.class, special.scenario().commands().getFirst());
        assertEquals(40, prelude.scalar14());
        var attack = assertInstanceOf(ScenarioNormalCommand.class, special.scenario().commands().get(1));
        assertEquals(14, attack.scalar14());
        var guided = special.scenario().commands().stream().filter(ScenarioGuidedCommand.class::isInstance)
            .map(ScenarioGuidedCommand.class::cast).findFirst().orElseThrow();
        assertEquals(4, guided.weapon().variant());
        assertEquals(1113, guided.target().x());
        assertEquals(662, guided.target().y());
    }
}
