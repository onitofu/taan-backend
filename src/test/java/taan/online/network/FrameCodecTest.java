package taan.online.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.IdleStateHandler;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import taan.online.TestFixtures;

class FrameCodecTest {
    @Test
    void reassemblesIndependentFrameAtEverySplit() {
        byte[] frame = TestFixtures.fixture("create-probe_only.frame.hex");
        byte[] root = TestFixtures.fixture("create-probe_only.root.hex");

        for (int split = 1; split < frame.length; split++) {
            var channel = new EmbeddedChannel(new FrameDecoder(1024, 10));

            assertFalse(channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOf(frame, split))));
            assertTrue(channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOfRange(frame, split, frame.length))));
            assertArrayEquals(root, channel.readInbound());

            channel.finishAndReleaseAll();
        }
    }

    @Test
    void separatesCoalescedFramesAndAcceptsExactLimit() {
        byte[] frame = TestFixtures.fixture("create-probe_only.frame.hex");
        var channel = new EmbeddedChannel(new FrameDecoder(1024, 10));
        var input = Unpooled.buffer().writeBytes(frame).writeBytes(frame);

        assertTrue(channel.writeInbound(input));
        assertArrayEquals(TestFixtures.fixture("create-probe_only.root.hex"), channel.readInbound());
        assertArrayEquals(TestFixtures.fixture("create-probe_only.root.hex"), channel.readInbound());
        assertNull(channel.readInbound());
        assertTrue(channel.writeInbound(Unpooled.buffer().writeIntLE(1024).writeZero(1024)));
        assertArrayEquals(new byte[1024], channel.readInbound());

        channel.finishAndReleaseAll();
    }

    @Test
    void rejectsUnsignedLengthsBeforeAllocation() {
        for (int length : new int[]{0, 3, 1025, -1}) {
            var channel = new EmbeddedChannel(new FrameDecoder(1024, 10));

            assertThrows(DecoderException.class, () -> channel.writeInbound(Unpooled.buffer().writeIntLE(length)));

            channel.pipeline().remove(FrameDecoder.class);
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void rejectsEofInEveryPartialFrame() {
        byte[] frame = TestFixtures.fixture("create-probe_only.frame.hex");

        for (int size = 1; size < frame.length; size++) {
            var channel = new EmbeddedChannel(new FrameDecoder(1024, 10));
            channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOf(frame, size)));

            assertThrows(DecoderException.class, channel::finishAndReleaseAll);
        }
    }

    @Test
    void closesSlowHeadersAndBodiesWithoutExtendingTheDeadlineOnEachByte() {
        for (byte[] prefix : new byte[][]{new byte[]{16}, new byte[]{16, 0, 0, 0}}) {
            var events = new ArrayList<Object>();
            var channel = new EmbeddedChannel(
                new IdleStateHandler(30, 0, 0),
                new FrameDecoder(1024, 10),
                new UserEventCollector(events)
            );
            channel.freezeTime();
            channel.writeInbound(Unpooled.wrappedBuffer(prefix));

            for (int second = 1; second < 10; second++) {
                channel.advanceTimeBy(1, TimeUnit.SECONDS);
                channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{0}));

                assertTrue(channel.isActive());
            }

            channel.advanceTimeBy(1, TimeUnit.SECONDS);
            channel.runScheduledPendingTasks();

            assertFalse(channel.isActive());
            assertEquals(List.of(FrameAssemblyTimeoutEvent.INSTANCE), events);
            assertNull(channel.readInbound());

            channel.finishAndReleaseAll();
        }
    }

    @Test
    void rejectsAnExpiredFrameEvenBeforeItsTimerCallbackRuns() {
        byte[] frame = TestFixtures.fixture("create-probe_only.frame.hex");
        var channel = new EmbeddedChannel(new FrameDecoder(1024, 10));
        channel.freezeTime();
        channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOf(frame, 1)));
        channel.advanceTimeBy(10, TimeUnit.SECONDS);
        channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOfRange(frame, 1, frame.length)));

        assertFalse(channel.isActive());
        assertNull(channel.readInbound());

        channel.finishAndReleaseAll();
    }

    @Test
    void givesEachNextPartialFrameItsOwnDeadlineAndCancelsCompletedFrames() {
        byte[] frame = TestFixtures.fixture("create-probe_only.frame.hex");
        var channel = new EmbeddedChannel(new FrameDecoder(1024, 10));
        channel.freezeTime();
        channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOf(frame, 1)));
        channel.advanceTimeBy(9, TimeUnit.SECONDS);
        channel.writeInbound(Unpooled.buffer().writeBytes(frame, 1, frame.length - 1).writeByte(frame[0]));

        assertArrayEquals(TestFixtures.fixture("create-probe_only.root.hex"), channel.readInbound());

        channel.advanceTimeBy(2, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        assertTrue(channel.isActive());

        channel.writeInbound(Unpooled.wrappedBuffer(Arrays.copyOfRange(frame, 1, frame.length)));

        assertArrayEquals(TestFixtures.fixture("create-probe_only.root.hex"), channel.readInbound());

        channel.advanceTimeBy(20, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        assertTrue(channel.isActive());

        channel.finishAndReleaseAll();
    }

    @Test
    void cancelsAssemblyTimerWhenDecoderIsRemoved() {
        var channel = new EmbeddedChannel(new FrameDecoder(1024, 10));
        channel.freezeTime();
        channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{4}));
        channel.pipeline().remove(FrameDecoder.class);
        ByteBuf leftover = channel.readInbound();
        leftover.release();
        channel.advanceTimeBy(11, TimeUnit.SECONDS);
        channel.runScheduledPendingTasks();

        assertTrue(channel.isActive());

        channel.finishAndReleaseAll();
    }

    @Test
    void writesIndependentFrameAndHandlesCompleteFinalDecode() {
        var channel = new EmbeddedChannel(new FrameEncoder());
        channel.writeOutbound(TestFixtures.fixture("create-probe_only.root.hex"));
        ByteBuf output = channel.readOutbound();
        byte[] bytes = new byte[output.readableBytes()];
        output.readBytes(bytes).release();

        assertArrayEquals(TestFixtures.fixture("create-probe_only.frame.hex"), bytes);

        channel.finishAndReleaseAll();
        var minimal = new EmbeddedChannel(new FrameDecoder(4, 10));
        byte[] frame = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(4).putInt(0).array();
        minimal.writeInbound(Unpooled.wrappedBuffer(frame));

        assertArrayEquals(new byte[4], minimal.readInbound());

        minimal.finishAndReleaseAll();
    }
}
