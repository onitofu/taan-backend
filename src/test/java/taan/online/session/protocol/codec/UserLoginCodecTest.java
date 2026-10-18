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
            TestFixtures.fixture("login-success-empty-female-local-account1.root.hex"),
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
    void changesOnlyTheLocalIdentityOfTheLegacySyntheticLoginResponse() {
        byte[] legacy = TestFixtures.fixture("login-success-empty-female.root.hex");
        byte[] current = new UserLoginResponseCodec().encode(new PrUserLogin(true, 1, 2, 0));

        assertEquals(0, ByteBuffer.wrap(legacy).order(ByteOrder.LITTLE_ENDIAN).getInt(5));
        assertEquals(1, ByteBuffer.wrap(current).order(ByteOrder.LITTLE_ENDIAN).getInt(5));
        assertArrayEquals(Arrays.copyOfRange(legacy, 0, 5), Arrays.copyOfRange(current, 0, 5));
        assertArrayEquals(Arrays.copyOfRange(legacy, 9, legacy.length), Arrays.copyOfRange(current, 9, current.length));
    }

    @Test
    void usesTheVerifiedAccountAsBothLocalIdentityAndAccountInfoIdentity() {
        for (long account : new long[]{1, 17, 0xffff_ffffL}) {
            byte[] response = new UserLoginResponseCodec().encode(new PrUserLogin(true, account, 2, 0));
            var input = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN);

            assertEquals(0x1995045a, input.getInt());
            assertEquals(1, input.get());
            assertEquals(account, Integer.toUnsignedLong(input.getInt()));
            assertEquals(0, input.getInt());
            assertEquals(1, input.getInt());
            assertEquals(0x19500459, input.getInt());
            assertEquals(account, Integer.toUnsignedLong(input.getInt()));
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
