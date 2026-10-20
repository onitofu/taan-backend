package taan.online;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

public final class CrossbowFixtures {
    private CrossbowFixtures() {
    }

    public static byte[] root(String name) throws IOException {
        try (var input = CrossbowFixtures.class.getResourceAsStream(
            "/match/weapons/user-crossbow-" + name + ".root.hex"
        )) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }
    }
}
