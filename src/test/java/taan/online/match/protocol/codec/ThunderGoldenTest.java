package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.match.controller.ScenarioClaimsMapper;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.match.service.model.ScenarioAnimationClaim;

class ThunderGoldenTest {
    @Test
    void retainsTheNativeThunderSpecialPreludeFireIdentityAndOpaqueFields() throws Exception {
        byte[] root;

        try (var input = getClass().getResourceAsStream("/match/weapons/live-special-a-scenario.root.hex")) {
            root = HexFormat.of().parseHex(new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                .replaceAll("\\s+", ""));
        }

        var codec = new ScenarioCodec();
        var packet = codec.decode(root);
        var marker = assertInstanceOf(ScenarioNormalCommand.class, packet.scenario().commands().getFirst());
        assertEquals(14, marker.header().kind());
        assertEquals(40, marker.scalar14());
        assertEquals(8, marker.scalar18());
        var fire = packet.scenario().commands().stream().filter(ScenarioFireCommand.class::isInstance)
            .map(ScenarioFireCommand.class::cast).findFirst().orElseThrow();
        assertEquals(10, fire.weaponId());
        assertEquals(2, fire.attackKind());
        assertEquals(2, fire.weapon().variant());
        assertEquals(3200, fire.weapon().shotTick());
        assertEquals(
            40,
            assertInstanceOf(
                ScenarioAnimationClaim.class,
                new ScenarioClaimsMapper().map(packet).commands().getFirst()
            ).animationId()
        );
        assertArrayEquals(root, codec.encode(packet));
    }
}
