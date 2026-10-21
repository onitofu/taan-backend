package taan.online;

@FunctionalInterface
public interface MatchResultReader {
    byte[] read() throws Exception;
}
