package taan.online;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;

public final class PostmatchFixtures {
    private PostmatchFixtures() {
    }

    public static byte[] bytes(String name) throws IOException {
        try (var input = Objects.requireNonNull(PostmatchFixtures.class.getResourceAsStream("/postmatch/" + name))) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }
    }
}
