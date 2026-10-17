package taan.online.bootstrap;

import java.io.IOException;
import java.io.InputStream;

final class BrokenDefinitionStream extends InputStream {
    private final byte[] bytes;
    private final boolean failRead;
    private int position;

    BrokenDefinitionStream(byte[] bytes, boolean failRead) {
        this.bytes = bytes.clone();
        this.failRead = failRead;
    }

    @Override
    public int read() throws IOException {
        if (failRead) {
            throw new IOException("Definition read failed");
        }

        return position < bytes.length ? Byte.toUnsignedInt(bytes[position++]) : -1;
    }

    @Override
    public void close() throws IOException {
        throw new IOException("Definition close failed");
    }
}
