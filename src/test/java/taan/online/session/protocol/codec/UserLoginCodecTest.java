package taan.online.session.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import taan.online.TestFixtures;
import taan.online.protocol.MalformedPacketException;
import taan.online.session.protocol.packet.PrUserLogin;

class UserLoginCodecTest {
    @Test
    void matchesIndependentLoginRequestsAndResponses() {
        var request = new UserLoginRequestCodec().decode(TestFixtures.fixture("login-probe_only.root.hex"));

        assertEquals(1, request.accounts().size());
        assertEquals(TestFixtures.LOGIN, request.accounts().getFirst().login());
        assertEquals(32, request.accounts().getFirst().credential().length());
        assertFalse(request.toString().contains(TestFixtures.LOGIN));
        assertFalse(request.accounts().getFirst().toString().contains(TestFixtures.LOGIN));
        assertEquals(
            2,
            new UserLoginRequestCodec().decode(
                TestFixtures.fixture("login-two-local-accounts.root.hex")
            ).accounts().size()
        );

        var codec = new UserLoginResponseCodec();

        assertArrayEquals(
            TestFixtures.fixture("login-success-empty-female.root.hex"),
            codec.encode(new PrUserLogin(true, 1, 2, 0))
        );

        for (int code : new int[]{0, 1, 2, 7}) {
            assertArrayEquals(
                TestFixtures.fixture("login-failure-code" + code + ".root.hex"),
                codec.encode(new PrUserLogin(false, 0, 0, code))
            );
        }
    }

    @Test
    void rejectsEveryTruncationInvalidTypeCountAndTrailingBytes() {
        byte[] root = TestFixtures.fixture("login-probe_only.root.hex");
        var codec = new UserLoginRequestCodec();

        for (int length = 0; length < root.length; length++) {
            byte[] truncated = Arrays.copyOf(root, length);
            assertThrows(MalformedPacketException.class, () -> codec.decode(truncated));
        }

        for (int offset : new int[]{0, 4, 8, 12, root.length - 11}) {
            byte[] broken = root.clone();
            ByteBuffer.wrap(broken).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, -1);
            assertThrows(MalformedPacketException.class, () -> codec.decode(broken));
        }

        assertThrows(MalformedPacketException.class, () -> codec.decode(Arrays.copyOf(root, root.length + 1)));

        byte[] arbitraryOpaque = root.clone();
        ByteBuffer.wrap(arbitraryOpaque).order(ByteOrder.LITTLE_ENDIAN).putInt(root.length - 15, 12345678);

        assertEquals(1, codec.decode(arbitraryOpaque).accounts().size());
    }
}
