package taan.online.account.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.password4j.Argon2Function;
import com.password4j.types.Argon2;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.TestFixtures;
import taan.online.account.service.model.ClientCredential;

class Argon2idCredentialHasherTest {
    @Test
    void hashesReceivedAsciiCredentialWithIndependentRandomSalts() {
        var hasher = new Argon2idCredentialHasher(19456, 2, 1);
        var first = hasher.hash(new ClientCredential(TestFixtures.CREDENTIAL));
        var second = hasher.hash(new ClientCredential(TestFixtures.CREDENTIAL));

        assertNotEquals(first, second);
        assertTrue(first.encoded().startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));

        String[] parts = first.encoded().split("\\$");

        assertEquals(16, Base64.getDecoder().decode(parts[4]).length);
        assertEquals(32, Base64.getDecoder().decode(parts[5]).length);
        assertTrue(
            Argon2Function.getInstanceFromHash(first.encoded())
                .check(TestFixtures.CREDENTIAL, first.encoded())
        );
    }

    @Test
    void matchesIndependentReferenceLibargon2Vector() {
        var function = Argon2Function.getInstance(19456, 2, 1, 32, Argon2.ID, 19);
        var result = function.hash(
            TestFixtures.CREDENTIAL.getBytes(StandardCharsets.US_ASCII),
            "0123456789abcdef".getBytes(StandardCharsets.US_ASCII)
        );

        assertArrayEquals(
            HexFormat.of().parseHex("db46634d95737eafff1f053e3b800b93f70dcc35f75467b7142e520607178559"),
            result.getBytes()
        );
    }

    @Test
    void keepsConcurrentCredentialsIndependentWithMultipleLanes() throws Exception {
        var hasher = new Argon2idCredentialHasher(19456, 2, 2);
        var barrier = new CyclicBarrier(2);

        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return hasher.hash(new ClientCredential("0".repeat(32)));
                }
            );
            var second = threads.submit(
                () -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return hasher.hash(new ClientCredential("1".repeat(32)));
                }
            );
            var firstResult = first.get(10, TimeUnit.SECONDS);
            var secondResult = second.get(10, TimeUnit.SECONDS);

            assertTrue(
                Argon2Function.getInstanceFromHash(firstResult.encoded())
                    .check("0".repeat(32), firstResult.encoded())
            );
            assertTrue(
                Argon2Function.getInstanceFromHash(secondResult.encoded())
                    .check("1".repeat(32), secondResult.encoded())
            );
            assertFalse(
                Argon2Function.getInstanceFromHash(firstResult.encoded())
                    .check("1".repeat(32), firstResult.encoded())
            );
        }
    }

    @Test
    void rejectsWeakOrInconsistentParameters() {
        assertThrows(IllegalArgumentException.class, () -> new Argon2idCredentialHasher(19455, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new Argon2idCredentialHasher(19456, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new Argon2idCredentialHasher(19456, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new Argon2idCredentialHasher(19456, 2, 2500));
    }
}
