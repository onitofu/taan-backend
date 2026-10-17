package taan.online;

public record CharacterCatalogEntry(long id, String name, int level, long gold, int gender, int mode, byte[] parts) {
    public CharacterCatalogEntry {
        parts = parts.clone();
    }

    @Override
    public byte[] parts() {
        return parts.clone();
    }
}
