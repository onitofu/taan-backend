package taan.online.match.view;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;
import taan.online.match.protocol.codec.EndGameCodec;
import taan.online.match.protocol.packet.GameResult;
import taan.online.match.protocol.packet.GameResultSlot;
import taan.online.match.protocol.packet.PcGameSessionEndGame;
import taan.online.match.service.MatchOutput;
import taan.online.protocol.PacketOutput;

public final class ProtocolMatchView implements MatchOutput {
    private static final byte[] END_PREVIEW = new EndGameCodec().encode(
        new PcGameSessionEndGame(
            new GameResult(
                0,
                IntStream.range(0, 8).mapToObj(
                    index -> new GameResultSlot(index, 0, 0, 0, 0, 0, 0, 0, List.of(0, 0, 0), 0, 0, 0)
                ).toList(),
                0,
                0
            ),
            0
        )
    );

    private final PacketOutput output;

    public ProtocolMatchView(PacketOutput output) {
        this.output = output;
    }

    @Override
    public CompletionStage<Void> endPreview() {
        return output.send(List.of(END_PREVIEW.clone()));
    }

    @Override
    public void close(String reason) {
        output.close(reason);
    }
}
