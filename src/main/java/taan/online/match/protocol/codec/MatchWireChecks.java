package taan.online.match.protocol.codec;

import java.util.HashSet;
import java.util.List;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.MalformedPacketException;

final class MatchWireChecks {
    private MatchWireChecks() {
    }

    static int count(long value, int maximum, int remaining, int entryBytes) {
        if (value > maximum || value > remaining / entryBytes) {
            throw new MalformedPacketException("INVALID_MATCH_VECTOR_COUNT");
        }

        return (int) value;
    }

    static void slot(int value) {
        if (value < 0 || value > 7) {
            throw new MalformedPacketException("INVALID_MATCH_SLOT");
        }
    }

    static void inventoryIndex(int value) {
        if (value < 0 || value > 2) {
            throw new MalformedPacketException("INVALID_INVENTORY_INDEX");
        }
    }

    static void facing(int value) {
        if (value < 0 || value > 1) {
            throw new MalformedPacketException("INVALID_MATCH_FACING");
        }
    }

    static void slots(List<Integer> values, int minimum, int maximum) {
        if (values.size() < minimum || values.size() > maximum) {
            throw new MalformedPacketException("INVALID_MATCH_SLOT_COUNT");
        }

        var seen = new HashSet<Integer>();

        for (int value : values) {
            slot(value);

            if (!seen.add(value)) {
                throw new MalformedPacketException("DUPLICATE_MATCH_SLOT");
            }
        }
    }

    static void triple(List<Integer> values) {
        if (values.size() != 3) {
            throw new MalformedPacketException("INVALID_MATCH_TRIPLE");
        }
    }

    static void resultSlots(List<Integer> values) {
        if (values.size() != 8) {
            throw new MalformedPacketException("INVALID_MATCH_RESULT_COUNT");
        }

        for (int index = 0; index < 8; index++) {
            if (values.get(index) != index) {
                throw new MalformedPacketException("INVALID_MATCH_RESULT_ORDER");
            }
        }
    }

    static void emptyVector(BinaryReader input) {
        if (input.unsignedInt() != 0) {
            throw new MalformedPacketException("UNSUPPORTED_MATCH_VECTOR");
        }
    }
}
