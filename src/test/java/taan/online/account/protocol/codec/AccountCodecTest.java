package taan.online.account.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import taan.online.TestFixtures;
import taan.online.account.protocol.packet.PrCheckAccount;
import taan.online.account.protocol.packet.PrCreateAccount;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.MalformedPacketException;
import taan.online.protocol.connection.FirstAcceptCodec;
import taan.online.protocol.connection.PcFirstAccept;

class AccountCodecTest {
    @Test
    void readsIndependentRegistrationAndAvailabilityFixtures() {
        var create = new CreateAccountRequestCodec().decode(TestFixtures.fixture("create-probe_only.root.hex"));

        assertEquals(TestFixtures.LOGIN, create.login());
        assertEquals(32, create.credential().length());
        assertEquals(1, create.gender());
        assertEquals(0, create.selector());
        assertFalse(create.toString().contains(create.credential()));
        assertFalse(create.toString().contains(create.login()));

        var check = new CheckAccountRequestCodec().decode(TestFixtures.fixture("check-local-account.root.hex"));

        assertEquals(TestFixtures.LOGIN, check.login());
        assertEquals(0, check.selector());
        assertFalse(check.toString().contains(check.login()));
    }

    @Test
    void writesGoldenRepliesAndZeroKeyGreeting() {
        for (int status : new int[]{0, 1, 255}) {
            for (int selector : new int[]{0, 1}) {
                assertArrayEquals(
                    TestFixtures.fixture("PrCheckAccount-status" + status + "-selector" + selector + ".root.hex"),
                    new CheckAccountResponseCodec().encode(new PrCheckAccount(status, selector))
                );
                assertArrayEquals(
                    TestFixtures.fixture("PrCreateAccount-status" + status + "-selector" + selector + ".root.hex"),
                    new CreateAccountResponseCodec().encode(new PrCreateAccount(status, selector))
                );
            }
        }

        assertArrayEquals(
            HexFormat.of().parseHex("0b051a220000000023b615a8000000000000000000"),
            new FirstAcceptCodec().encode(new PcFirstAccept(0xa815b62300000000L, 0, 0))
        );
    }

    @Test
    void rejectsEveryTruncationAndTrailingBytes() {
        byte[] create = TestFixtures.fixture("create-probe_only.root.hex");

        for (int size = 0; size < create.length; size++) {
            byte[] truncated = Arrays.copyOf(create, size);

            assertThrows(MalformedPacketException.class, () -> new CreateAccountRequestCodec().decode(truncated));
        }

        byte[] check = TestFixtures.fixture("check-local-account.root.hex");

        for (int size = 0; size < check.length; size++) {
            byte[] truncated = Arrays.copyOf(check, size);

            assertThrows(MalformedPacketException.class, () -> new CheckAccountRequestCodec().decode(truncated));
        }

        assertThrows(
            MalformedPacketException.class,
            () -> new CreateAccountRequestCodec().decode(Arrays.copyOf(create, create.length + 1))
        );
        assertThrows(
            MalformedPacketException.class,
            () -> new CheckAccountRequestCodec().decode(Arrays.copyOf(check, check.length + 1))
        );
    }

    @Test
    void rejectsWrongTypeOverflowAndMalformedUtf16() {
        assertThrows(MalformedPacketException.class, () -> new BinaryReader(new byte[4], 1));

        byte[] overflow = HexFormat.of().parseHex("6c05f126ffffffff");

        assertThrows(MalformedPacketException.class, () -> new CheckAccountRequestCodec().decode(overflow));
        assertThrows(
            MalformedPacketException.class,
            () -> new CreateAccountRequestCodec()
                .decode(TestFixtures.fixture("negative-create-surrogate.root.hex"))
        );
    }
}
