package taan.online.match.protocol.codec;

import taan.online.protocol.MalformedPacketException;

final class ScenarioWireChecks {
    private ScenarioWireChecks() { }

    static void slot(int value) {
        if (value < 0 || value > 7) {
            throw new MalformedPacketException("INVALID_SCENARIO_SLOT");
        }
    }

    static void finite(float value) {
        if (!Float.isFinite(value)) {
            throw new MalformedPacketException("NONFINITE_SCENARIO_VALUE");
        }
    }
}
