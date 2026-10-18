package taan.online;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import taan.online.bootstrap.MatchDefinitionLoader;
import taan.online.match.model.MatchDefinitions;
import taan.online.match.controller.MatchPacketHandler;
import taan.online.match.service.MatchRegistry;
import taan.online.match.service.TerminateMatchAction;
import taan.online.match.service.ConsumeMatchResultAction;
import taan.online.room.service.RoomMatchAccess;
import taan.online.server.MatchRequestDispatcher;

public final class MatchFixtures {
    private MatchFixtures() {
    }

    public static MatchRequestDispatcher dispatcher(RoomMatchAccess rooms, MatchRegistry matches) {
        return new MatchRequestDispatcher(
            new MatchPacketHandler(
                rooms,
                matches,
                new TerminateMatchAction(rooms),
                new ConsumeMatchResultAction(rooms, matches)
            )
        );
    }

    public static byte[] root(String name) throws IOException {
        try (var input = MatchFixtures.class.getResourceAsStream("/match/" + name + ".root.hex")) {
            return HexFormat.of().parseHex(
                new String(
                    input.readAllBytes(),
                    StandardCharsets.US_ASCII
                ).replaceAll("\\s+", "")
            );
        }
    }

    public static MatchDefinitions definitions() {
        return new MatchDefinitionLoader().load(
            MatchFixtures.class.getResourceAsStream("/match/jungle.properties"),
            MatchFixtures.class.getResourceAsStream("/match/jungle-spawns.csv"),
            MatchFixtures.class.getResourceAsStream("/match/weapons.csv")
        );
    }
}
