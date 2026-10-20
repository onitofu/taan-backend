package taan.online;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import taan.online.match.service.model.MatchLootPolicy;

public final class MatchDamageUpTestPackets {
    public static final List<Integer> ITEMS = List.of(1005, 1010, 1015, 1020, 1025, 1030, 1035, 1040, 1045, 1050);
    public static final MatchLootPolicy POLICY = new MatchLootPolicy(
        Set.of(1, 2, 5, 1005, 1010, 1015, 1020, 1025, 1030, 1035, 1040, 1045, 1050)
    );

    private MatchDamageUpTestPackets() {
    }

    public static byte[] root(String name) throws IOException {
        try (var input = MatchDamageUpTestPackets.class.getResourceAsStream("/match/damage-up/" + name)) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }
    }
}
