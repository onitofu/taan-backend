package taan.online.match.service.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class AcceptedScenario {
    private final MatchCommandContext sender;
    private final long turnRevision;
    private final byte[] root;
    private final String hash;
    private final ScenarioJournal journal;

    public AcceptedScenario(MatchCommandContext sender, long turnRevision, byte[] root, ScenarioJournal journal) {
        this.sender = sender;
        this.turnRevision = turnRevision;
        this.root = root.clone();
        this.hash = hash(this.root);
        this.journal = journal;
    }

    public MatchCommandContext sender() {
        return sender;
    }

    public long turnRevision() {
        return turnRevision;
    }

    public byte[] root() {
        return root.clone();
    }

    public String hash() {
        return hash;
    }

    public ScenarioJournal journal() {
        return journal;
    }

    public static String hash(byte[] root) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(root));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    @Override
    public String toString() {
        return "AcceptedScenario[REDACTED]";
    }
}
