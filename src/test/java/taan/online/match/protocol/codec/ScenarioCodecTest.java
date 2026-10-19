package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioBaseCommand;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioFloatPoint;
import taan.online.match.protocol.packet.ScenarioGuidedCommand;
import taan.online.match.protocol.packet.ScenarioIntPoint;
import taan.online.match.protocol.packet.ScenarioMakeItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.match.protocol.packet.ScenarioTail;
import taan.online.match.protocol.packet.ScenarioTeamGaugeCommand;
import taan.online.match.protocol.packet.ScenarioUseBonusCommand;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.protocol.packet.ScenarioWeaponEffect;
import taan.online.match.protocol.packet.ScenarioWeaponFields;
import taan.online.protocol.MalformedPacketException;

class ScenarioCodecTest {
    private final ScenarioCodec codec = new ScenarioCodec();

    @Test
    void retainsNativeRootsAndTheirIndependentProvenance() throws Exception {
        String provenance;

        try (var input = getClass().getResourceAsStream("/match/scenario-provenance.json")) {
            provenance = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (String name : List.of("native-cannon-0-0", "native-cannon-2-1", "native-cannon-2-0")) {
            byte[] root = MatchFixtures.root(name);
            var packet = codec.decode(root);

            assertArrayEquals(root, codec.encode(packet));
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root));
            assertTrue(provenance.contains(hash));
            assertEquals(0, ((ScenarioFireCommand) packet.scenario().commands().get(1)).weaponId());

            for (int size = 0; size < root.length; size++) {
                byte[] truncated = Arrays.copyOf(root, size);
                assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
            }

            assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(root, root.length + 1)));
        }
    }

    @Test
    void rejectsMalformedIncomingKindsSlotsFacingPowerAndEffectScalars() throws Exception {
        byte[] nativeRoot = MatchFixtures.root("native-cannon-0-0");

        for (int offset : new int[]{4, 5, 10, 20}) {
            byte[] invalid = nativeRoot.clone();
            invalid[offset] = (byte) 255;

            assertThrows(MalformedPacketException.class, () -> codec.decode(invalid));
        }

        byte[] incompatible = nativeRoot.clone();
        incompatible[21] = 0;
        assertThrows(MalformedPacketException.class, () -> codec.decode(incompatible));
        byte[] simple = codec.encode(packet(List.of()));
        ByteBuffer.wrap(simple).order(ByteOrder.LITTLE_ENDIAN).putInt(26, 0x7fc00000);
        assertThrows(MalformedPacketException.class, () -> codec.decode(simple));

        for (int offset : new int[]{29, 33}) {
            var scene = new GameScenario(
                0,
                0,
                List.of(),
                List.of(),
                List.of(new ScenarioWeaponEffect(0, 1, 2, 3, 4)),
                0,
                0,
                List.of(),
                0
            );
            var packet = new PcGamePlayScenario(0, 0, scene, 0, 0, new ScenarioTail(0, 0, 0, 0, 0, 0));
            byte[] invalid = codec.encode(packet);
            ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, 0x7fc00000);

            assertThrows(MalformedPacketException.class, () -> codec.decode(invalid));
        }
    }

    @Test
    void checksEveryDescriptorKindPairAndPreservesAllTenLayouts() {
        int compatible = 0;

        for (int layout = 0; layout < 10; layout++) {
            for (int kind = 0; kind < 18; kind++) {
                var command = command(layout, new ScenarioCommandHeader(0, kind, -1, Integer.MIN_VALUE));
                boolean valid = switch (kind) {
                    case 0 -> layout == 2;
                    case 1, 2, 3 -> layout == 1 || layout == 2 || layout == 3;
                    case 4 -> layout == 5;
                    case 5, 7, 8 -> true;
                    case 6, 9, 14, 16 -> layout == 4;
                    case 10 -> layout == 6;
                    case 11 -> layout == 7;
                    case 12 -> layout == 8;
                    case 13 -> layout == 3;
                    case 15 -> layout == 9;
                    default -> false;
                };

                if (valid) {
                    compatible++;
                    var packet = packet(List.of(command));

                    assertEquals(packet, codec.decode(codec.encode(packet)));
                } else {
                    assertThrows(MalformedPacketException.class, () -> codec.encode(packet(List.of(command))));
                }
            }
        }

        assertEquals(50, compatible);
    }

    @Test
    void rejectsUnknownDescriptorsOversizedVectorsAndInvalidMeaningfulScalars() throws Exception {
        byte[] root = MatchFixtures.root("native-cannon-0-0");

        for (int offset : new int[]{0, 6, 16}) {
            byte[] invalid = root.clone();
            ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, -1);

            assertThrows(MalformedPacketException.class, () -> codec.decode(invalid));
        }

        for (long count : new long[]{4097, 0xffff_ffffL, 100}) {
            byte[] invalid = root.clone();
            ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN).putInt(12, (int) count);

            assertThrows(MalformedPacketException.class, () -> codec.decode(invalid));
        }

        assertThrows(MalformedPacketException.class, () -> codec.decode(new byte[65537]));
        assertThrows(
            MalformedPacketException.class,
            () -> codec.encode(packet(Collections.nCopies(4097, command(0, h(8)))))
        );
        assertThrows(
            MalformedPacketException.class,
            () -> codec.encode(packet(Collections.nCopies(4096, command(2, h(0)))))
        );

        for (int slot : new int[]{-1, 8}) {
            assertThrows(
                MalformedPacketException.class,
                () -> codec.encode(packet(List.of(command(0, new ScenarioCommandHeader(slot, 8, 0, 0)))))
            );
        }

        var base = packet(List.of());

        for (int facing : new int[]{-1, 2}) {
            assertThrows(
                MalformedPacketException.class,
                () -> codec.encode(new PcGamePlayScenario(facing, 0, base.scenario(), 0, 0, base.tail()))
            );
        }

        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY}) {
            var scene = new GameScenario(0, 0, List.of(), List.of(), List.of(), 0, value, List.of(), 0);

            assertThrows(
                MalformedPacketException.class,
                () -> codec.encode(new PcGamePlayScenario(0, 0, scene, 0, 0, base.tail()))
            );
        }
    }

    @Test
    void preservesOpaqueNanBitsButRejectsNonfiniteTerrainAndFireVectors() {
        var fields = new ScenarioWeaponFields(0, 0, new ScenarioIntPoint(-10, 999), 700, 0x7fa12345, 255);
        var fire = new ScenarioFireCommand(
            h(0),
            fields,
            0,
            0,
            new ScenarioFloatPoint(1, 2),
            new ScenarioFloatPoint(3, 4),
            1,
            20
        );
        var guided = new ScenarioGuidedCommand(h(13), fields, new ScenarioIntPoint(123, 456));
        var packet = packet(List.of(fire, guided));

        assertEquals(packet, codec.decode(codec.encode(packet)));
        assertThrows(
            MalformedPacketException.class,
            () -> codec.encode(packet(List.of(new ScenarioWeaponCommand(h(1), fields))))
        );

        for (int component = 0; component < 4; component++) {
            float[] values = {1, 2, 3, 4};
            values[component] = Float.NaN;
            var bad = new ScenarioFireCommand(
                h(0),
                fields,
                0,
                0,
                new ScenarioFloatPoint(values[0], values[1]),
                new ScenarioFloatPoint(values[2], values[3]),
                1,
                20
            );

            assertThrows(MalformedPacketException.class, () -> codec.encode(packet(List.of(bad))));
        }
    }

    @Test
    void retainsEffectsSelectedItemsRefittedSlotsAndRejectsBadEffectDescriptors() {
        var effect = new ScenarioWeaponEffect(255, 1.25f, -7, 65535, 123);
        var scene = new GameScenario(
            7,
            255,
            List.of(),
            List.of(1, 65535),
            List.of(effect),
            65535,
            .5f,
            List.of(0, 7),
            -1
        );
        var packet = new PcGamePlayScenario(1, 7, scene, -1, 65535, new ScenarioTail(-1, 2, 255, 3, 4, 5));
        byte[] root = codec.encode(packet);

        assertEquals(packet, codec.decode(root));
        ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN).putInt(28, -1);
        assertThrows(MalformedPacketException.class, () -> codec.decode(root));

        for (var invalid : List.of(
            new ScenarioWeaponEffect(1, Float.NaN, 0, 0, 0),
            new ScenarioWeaponEffect(1, 0, Float.NaN, 0, 0)
        )) {
            var bad = new GameScenario(0, 0, List.of(), List.of(), List.of(invalid), 0, 0, List.of(), 0);

            assertThrows(
                MalformedPacketException.class,
                () -> codec.encode(new PcGamePlayScenario(0, 0, bad, 0, 0, packet.tail()))
            );
        }
    }

    private ScenarioCommandHeader h(int kind) {
        return new ScenarioCommandHeader(0, kind, 0, 0);
    }

    private PcGamePlayScenario packet(List<ScenarioCommand> commands) {
        return new PcGamePlayScenario(
            0,
            0,
            new GameScenario(0, 0, commands, List.of(), List.of(), 0, .5f, List.of(), 0),
            0,
            0,
            new ScenarioTail(0, 0, 0, 0, 0, 0)
        );
    }

    private ScenarioCommand command(int layout, ScenarioCommandHeader h) {
        var w = new ScenarioWeaponFields(
            255,
            254,
            new ScenarioIntPoint(Integer.MIN_VALUE, Integer.MAX_VALUE),
            -1,
            0x3f800000,
            253
        );
        return switch (layout) {
            case 0 -> new ScenarioBaseCommand(h);
            case 1 -> new ScenarioWeaponCommand(h, w);
            case 2 -> new ScenarioFireCommand(
                h,
                w,
                65535,
                255,
                new ScenarioFloatPoint(-1, 2),
                new ScenarioFloatPoint(3, -4),
                255,
                65535
            );
            case 3 -> new ScenarioGuidedCommand(h, w, new ScenarioIntPoint(-100, 100));
            case 4 -> new ScenarioNormalCommand(h, -1, Integer.MIN_VALUE);
            case 5 -> new ScenarioDamageCommand(h, -100, -1, 65535, 65535);
            case 6 -> new ScenarioMakeItemBoxCommand(h, Long.MIN_VALUE, new ScenarioIntPoint(1, 2));
            case 7 -> new ScenarioEatItemBoxCommand(h, Long.MAX_VALUE, 65535, Integer.MIN_VALUE);
            case 8 -> new ScenarioTeamGaugeCommand(h, 255, 65535, 65534);
            case 9 -> new ScenarioUseBonusCommand(h, 65535);
            default -> throw new IllegalArgumentException("Unknown test layout");
        };
    }
}
