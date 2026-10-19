package taan.online.match.protocol.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioTail;
import taan.online.match.protocol.packet.ScenarioWeaponEffect;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class ScenarioCodec {
    public static final int MAX_ROOT_BYTES = 65536;
    public static final int MAX_VECTOR_ELEMENTS = 4096;
    private final ScenarioCommandCodec commands = new ScenarioCommandCodec();

    public PcGamePlayScenario decode(byte[] root) {
        if (root.length > MAX_ROOT_BYTES) {
            throw new MalformedPacketException("SCENARIO_TOO_LARGE");
        }

        var input = new BinaryReader(root, PcGamePlayScenario.TYPE_ID);
        int facing = input.unsignedByte();
        int shooter = input.unsignedByte();
        type(input, GameScenario.TYPE_ID);
        int slot = input.unsignedByte();
        int scalar09 = input.unsignedByte();
        var events = vector(input, 14, () -> commands.read(input));
        var selected = vector(input, 2, input::unsignedShort);
        var effects = vector(input, 17, () -> effect(input));
        int flags = input.unsignedShort();
        float power = input.float32();
        var refitted = vector(input, 1, input::unsignedByte);
        var scene = new GameScenario(
            slot,
            scalar09,
            events,
            selected,
            effects,
            flags,
            power,
            refitted,
            input.signedInt()
        );
        var result = new PcGamePlayScenario(
            facing,
            shooter,
            scene,
            input.signedInt(),
            input.unsignedShort(),
            tail(input)
        );
        input.finish();
        validate(result);
        return result;
    }

    public byte[] encode(PcGamePlayScenario packet) {
        validate(packet);
        var output = new BinaryWriter().signedInt(PcGamePlayScenario.TYPE_ID)
            .unsignedByte(packet.facing()).unsignedByte(packet.shooterSlot());
        var scene = packet.scenario();
        output.signedInt(GameScenario.TYPE_ID).unsignedByte(scene.slot()).unsignedByte(scene.scalar09());
        vector(output, scene.commands(), value -> commands.write(output, value));
        vector(output, scene.selectedItems(), output::unsignedShort);
        vector(output, scene.effects(), value -> effect(output, value));
        output.unsignedShort(scene.flags()).float32(scene.power());
        vector(output, scene.refittedSlots(), output::unsignedByte);
        output.signedInt(scene.scalar0c()).signedInt(packet.scalar18()).unsignedShort(packet.scalar1c());
        var tail = packet.tail();
        output.signedInt(tail.value20()).signedInt(tail.value24()).unsignedByte(tail.byte28())
            .signedInt(tail.value2c()).signedInt(tail.value30()).signedInt(tail.value34());
        byte[] result = output.toByteArray();

        if (result.length > MAX_ROOT_BYTES) {
            throw new MalformedPacketException("SCENARIO_TOO_LARGE");
        }

        return result;
    }

    private void validate(PcGamePlayScenario value) {
        if (value.facing() < 0 || value.facing() > 1) {
            throw new MalformedPacketException("INVALID_SCENARIO_FACING");
        }

        ScenarioWireChecks.slot(value.shooterSlot());
        ScenarioWireChecks.slot(value.scenario().slot());
        ScenarioWireChecks.finite(value.scenario().power());
        value.scenario().refittedSlots().forEach(ScenarioWireChecks::slot);
    }

    private <T> List<T> vector(BinaryReader input, int minimumBytes, Supplier<T> read) {
        long count = input.unsignedInt();

        if (count > MAX_VECTOR_ELEMENTS || count > input.remaining() / minimumBytes) {
            throw new MalformedPacketException("INVALID_SCENARIO_VECTOR");
        }

        var values = new ArrayList<T>((int) count);

        for (int index = 0; index < count; index++) {
            values.add(read.get());
        }

        return values;
    }

    private <T> void vector(BinaryWriter output, List<T> values, Consumer<T> write) {
        if (values.size() > MAX_VECTOR_ELEMENTS) {
            throw new MalformedPacketException("INVALID_SCENARIO_VECTOR");
        }

        output.unsignedInt(values.size());
        values.forEach(write);
    }

    private ScenarioWeaponEffect effect(BinaryReader input) {
        type(input, ScenarioWeaponEffect.TYPE_ID);
        var value = new ScenarioWeaponEffect(
            input.unsignedByte(),
            input.float32(),
            input.float32(),
            input.unsignedShort(),
            input.unsignedShort()
        );
        ScenarioWireChecks.finite(value.param1());
        ScenarioWireChecks.finite(value.param2());
        return value;
    }

    private void effect(BinaryWriter output, ScenarioWeaponEffect value) {
        ScenarioWireChecks.finite(value.param1());
        ScenarioWireChecks.finite(value.param2());
        output.signedInt(ScenarioWeaponEffect.TYPE_ID).unsignedByte(value.kind())
            .float32(value.param1()).float32(value.param2())
            .unsignedShort(value.availableTurns()).unsignedShort(value.availableOwnTurns());
    }

    private ScenarioTail tail(BinaryReader input) {
        return new ScenarioTail(
            input.signedInt(),
            input.signedInt(),
            input.unsignedByte(),
            input.signedInt(),
            input.signedInt(),
            input.signedInt()
        );
    }

    private void type(BinaryReader input, int expected) {
        if (input.signedInt() != expected) {
            throw new MalformedPacketException("UNKNOWN_SCENARIO_DESCRIPTOR");
        }
    }
}
