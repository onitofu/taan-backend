package taan.online.match.protocol.codec;

import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.protocol.BinaryReader;

public final class EndScenarioCodec {
    public PcGamePlayEndScenario decode(byte[] root) {
        var input = new BinaryReader(root, PcGamePlayEndScenario.TYPE_ID);
        var result = new PcGamePlayEndScenario(input.unsignedByte(), input.unsignedByte());
        input.finish();
        MatchWireChecks.slot(result.slotIndex());
        return result;
    }
}
