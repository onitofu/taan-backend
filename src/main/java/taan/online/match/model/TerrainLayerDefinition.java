package taan.online.match.model;

public final class TerrainLayerDefinition {
    private final int left;
    private final int top;
    private final int width;
    private final int height;
    private final byte[] cells;

    public TerrainLayerDefinition(int left, int top, int width, int height, byte[] cells) {
        if (width < 1 || height < 1 || (long) width * height != cells.length
            || (long) left + width > Integer.MAX_VALUE || (long) top + height > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid terrain layer bounds or cells");
        }

        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.cells = cells.clone();
    }

    public int left() {
        return left;
    }

    public int top() {
        return top;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int value(int x, int y) {
        long column = (long) x - left;
        long row = (long) y - top;

        if (column < 0 || column >= width || row < 0 || row >= height) {
            return 0;
        }

        return Byte.toUnsignedInt(cells[(int) row * width + (int) column]);
    }

    public byte[] cells() {
        return cells.clone();
    }

    TerrainLayerDefinition replace(byte[] updated) {
        return new TerrainLayerDefinition(left, top, width, height, updated);
    }
}
