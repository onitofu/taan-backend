package taan.online.bootstrap;

import java.io.ByteArrayInputStream;

final class TrackingDefinitionStream extends ByteArrayInputStream {
    private boolean closed;

    TrackingDefinitionStream(byte[] bytes) {
        super(bytes);
    }

    @Override
    public void close() {
        closed = true;
    }

    boolean closed() {
        return closed;
    }
}
