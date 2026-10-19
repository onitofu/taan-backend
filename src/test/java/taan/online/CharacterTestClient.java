package taan.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import taan.online.character.protocol.packet.CharacterInfo;

public final class CharacterTestClient implements AutoCloseable {
    private final Socket socket;

    public CharacterTestClient(int port) throws IOException {
        socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(10000);

        assertEquals(21, read().length);
    }

    public byte[] login(String name) throws IOException {
        send(TestFixtures.fixture("first-packet-minimal.root.hex"));

        assertEquals(10, read().length);

        send(TestFixtures.login(name, TestFixtures.CREDENTIAL));
        byte[] reply = read();
        var identity = ByteBuffer.wrap(reply).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(0x1995045a, identity.getInt());
        assertEquals(1, identity.get());
        long localAccount = Integer.toUnsignedLong(identity.getInt());

        assertTrue(localAccount > 0);
        assertEquals(0, identity.getInt());
        assertEquals(1, identity.getInt());
        assertEquals(0x19500459, identity.getInt());
        assertEquals(localAccount, Integer.toUnsignedLong(identity.getInt()));
        return reply;
    }

    public void send(byte[] root) throws IOException {
        socket.getOutputStream().write(frame(root));
        socket.getOutputStream().flush();
    }

    public void sendTogether(byte[]... roots) throws IOException {
        var bytes = new ByteArrayOutputStream();

        for (var root : roots) {
            bytes.write(frame(root));
        }

        socket.getOutputStream().write(bytes.toByteArray());
        socket.getOutputStream().flush();
    }

    public void sendFragmented(byte[] root) throws IOException {
        byte[] frame = frame(root);

        for (int start = 0; start < frame.length; start += 7) {
            socket.getOutputStream().write(frame, start, Math.min(7, frame.length - start));
            socket.getOutputStream().flush();
        }
    }

    public byte[] read() throws IOException {
        var input = new DataInputStream(socket.getInputStream());
        int length = Integer.reverseBytes(input.readInt());

        assertTrue(length >= 4 && length <= 16384);

        byte[] root = input.readNBytes(length);

        assertEquals(length, root.length);
        return root;
    }

    public void awaitClosure() throws IOException {
        assertEquals(-1, socket.getInputStream().read());
    }

    public void resetConnection() throws IOException {
        socket.setSoLinger(true, 0);
        socket.close();
    }

    public void disconnect() throws IOException {
        socket.shutdownOutput();
        awaitClosure();
    }

    public static List<CharacterCatalogEntry> catalog(byte[] root, int offset) {
        var input = ByteBuffer.wrap(root).order(ByteOrder.LITTLE_ENDIAN);
        input.position(offset);

        assertEquals(0x343e0648, input.getInt());

        int count = input.getInt();
        var info = new ArrayList<CharacterInfo>();

        for (int index = 0; index < count; index++) {
            assertEquals(0x22d40519, input.getInt());

            long id = Integer.toUnsignedLong(input.getInt());
            String name = string(input);
            int level = Short.toUnsignedInt(input.getShort());
            input.position(input.position() + 12);
            long gold = Integer.toUnsignedLong(input.getInt());
            int gender = Byte.toUnsignedInt(input.get());
            input.position(input.position() + 36);

            assertEquals("", string(input));

            info.add(new CharacterInfo(id, name, level, gold, gender));
        }

        assertEquals(count, input.getInt());

        var result = new ArrayList<CharacterCatalogEntry>();

        for (var character : info) {
            assertEquals(0x2e1e05e6, input.getInt());

            int mode = Byte.toUnsignedInt(input.get());

            assertEquals(0, input.getInt());
            assertEquals(0, input.getShort());

            byte[] parts = new byte[160];
            input.get(parts);

            assertEquals(0, input.get());

            result.add(
                new CharacterCatalogEntry(
                    character.characterId(),
                    character.name(),
                    character.level(),
                    character.gold(),
                    character.gender(),
                    mode,
                    parts
                )
            );
        }

        return List.copyOf(result);
    }

    public static byte[] appearanceBytes(byte[] request) {
        return Arrays.copyOfRange(request, request.length - 160, request.length);
    }

    private static String string(ByteBuffer input) {
        byte[] bytes = new byte[input.getInt() * 2];
        input.get(bytes);
        return new String(bytes, StandardCharsets.UTF_16LE);
    }

    private static byte[] frame(byte[] root) {
        return ByteBuffer.allocate(root.length + 4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(root.length).put(root).array();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
