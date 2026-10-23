package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import taan.online.WeaponExpansionCase;
import taan.online.WeaponExpansionFixtures;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.protocol.MalformedPacketException;

class WeaponExpansionCodecTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("taan.online.WeaponExpansionFixtures#cases")
    void preservesIndependentNativeBytesAndOrderedLaunchesIncludingOpaqueResourceFields(
        WeaponExpansionCase evidence
    ) throws Exception {
        var root = WeaponExpansionFixtures.root(evidence);
        var codec = new ScenarioCodec();
        var packet = codec.decode(root);
        var commands = packet.scenario().commands();
        assertEquals(evidence.sha256(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root)));
        assertArrayEquals(root, codec.encode(packet));
        assertEquals(evidence.shooter(), packet.shooterSlot());
        assertEquals(evidence.shooter(), packet.scenario().slot());
        assertEquals(evidence.facing(), packet.facing());
        assertEquals(evidence.selectedItems(), packet.scenario().selectedItems());
        assertTrue(packet.scenario().effects().isEmpty());
        assertEquals(
            evidence.commandKinds(),
            commands.stream().map(c -> Integer.toString(c.header().kind())).collect(Collectors.joining(","))
        );
        assertEquals(
            evidence.animations(),
            commands.stream().filter(ScenarioNormalCommand.class::isInstance).map(ScenarioNormalCommand.class::cast)
                .filter(c -> c.header().kind() == 9 || c.header().kind() == 14)
                .map(c -> c.header().kind() + ":" + c.header().tick() + ":" + c.scalar14())
                .collect(Collectors.joining(";"))
        );
        assertEquals(
            evidence.damage(),
            commands.stream().filter(ScenarioDamageCommand.class::isInstance).map(ScenarioDamageCommand.class::cast)
                .mapToInt(ScenarioDamageCommand::damage).sum()
        );
        String fires = commands.stream().filter(ScenarioFireCommand.class::isInstance)
            .map(ScenarioFireCommand.class::cast).map(c -> c.weapon().sequence() + ":" + c.weapon().variant()
                + ":" + c.header().tick()).collect(Collectors.joining(";"));
        String expectedFires = Arrays.stream(evidence.fireBindings().split(";")).map(value -> {
            var fields = value.split(":");
            return fields[0] + ":" + fields[1] + ":" + fields[3];
        }).collect(Collectors.joining(";"));
        assertEquals(expectedFires, fires);
        assertTrue(
            commands.stream().filter(ScenarioFireCommand.class::isInstance).map(ScenarioFireCommand.class::cast)
                .allMatch(c -> c.weaponId() == evidence.weaponId() && c.attackKind() == evidence.attackKind())
        );

        for (int length : new int[]{0, 4, root.length / 2, root.length - 1}) {
            assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(root, length)));
        }
    }
}
