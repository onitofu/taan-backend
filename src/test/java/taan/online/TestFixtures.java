package taan.online;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public final class TestFixtures {
    public static final String LOGIN = "auth-fixture@example.test";
    public static final String CREDENTIAL = "0123456789abcdef0123456789abcdef";
    public static final String VERIFIER = "$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA";

    private TestFixtures() {
    }

    public static byte[] fixture(String name) {
        try (var stream = TestFixtures.class.getResourceAsStream("/auth/" + name)) {
            if (stream == null) {
                throw new IllegalArgumentException("Missing test fixture: " + name);
            }

            String hex = new String(stream.readAllBytes(), StandardCharsets.US_ASCII);
            return HexFormat.of().parseHex(hex.replaceAll("\\s+", ""));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static Map<String, String> environment() {
        var result = new HashMap<String, String>();

        try {
            for (String line : Files.readAllLines(Path.of(".env.example"))) {
                if (!line.isBlank()) {
                    String[] pair = line.split("=", 2);
                    result.put(pair[0], pair[1]);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }

        result.put("POSTGRES_PASSWORD", "public-test-password");
        return result;
    }

    public static byte[] check(String login, int selector) {
        byte[] text = login.getBytes(StandardCharsets.UTF_16LE);
        return ByteBuffer.allocate(9 + text.length).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x26f1056c).putInt(text.length / 2).put(text).put((byte) selector).array();
    }

    public static byte[] create(String login, String credential, int gender, int selector) {
        byte[] name = login.getBytes(StandardCharsets.UTF_16LE);
        byte[] digest = credential.getBytes(StandardCharsets.UTF_16LE);
        return ByteBuffer.allocate(14 + name.length + digest.length).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x2d6d05e2).putInt(name.length / 2).put(name).putInt(digest.length / 2).put(digest)
            .put((byte) gender).put((byte) selector).array();
    }
}
