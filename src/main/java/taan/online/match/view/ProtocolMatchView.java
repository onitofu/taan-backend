package taan.online.match.view;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;
import taan.online.character.controller.CharacterCatalogMapper;
import taan.online.match.model.MatchOutcome;
import taan.online.match.protocol.codec.EndGameCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.StartGameCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.packet.GameResult;
import taan.online.match.protocol.packet.GameResultSlot;
import taan.online.match.protocol.packet.GameSlotPlayInfo;
import taan.online.match.protocol.packet.PcGamePlaySetPos;
import taan.online.match.protocol.packet.PcGamePlayTurnStart;
import taan.online.match.protocol.packet.PcGameSessionEndGame;
import taan.online.match.protocol.packet.PcGameSessionStartGame;
import taan.online.match.service.MatchOutput;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.protocol.PacketOutput;
import taan.online.protocol.option.packet.OptionList;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomTeam;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.GameSlotInfo;
import taan.online.room.protocol.packet.GameSlotOccupant;
import taan.online.room.protocol.packet.PcGameSessionUpdateSlot;

public final class ProtocolMatchView implements MatchOutput {
    private final PacketOutput output;

    public ProtocolMatchView(PacketOutput output) {
        this.output = output;
    }

    @Override
    public CompletionStage<Void> start(MatchSnapshot snapshot) {
        var roots = new ArrayList<byte[]>();
        var roomCodec = new RoomResponseCodec();
        var appearance = new CharacterCatalogMapper();

        for (var member : snapshot.participants()) {
            var character = member.character();
            var occupant = new GameSlotOccupant(
                character.accountId().value(),
                character.id().value(),
                character.name().value(),
                "",
                character.level(),
                member.team() == RoomTeam.BLUE ? 1 : 2,
                appearance.appearance(character),
                member.weaponId(),
                new OptionList()
            );
            roots.add(
                roomCodec.encode(
                    new PcGameSessionUpdateSlot(
                        0,
                        new GameSlotInfo(
                            member.slotIndex(),
                            member.roomSlotState() == RoomSlotState.READY ? 3 : 2,
                            occupant
                        ),
                        ""
                    )
                )
            );
        }

        var players = snapshot.participants().stream().map(
            member -> new GameSlotPlayInfo(
                member.slotIndex(),
                member.spawn().rawX(),
                member.spawn().rawY(),
                member.facing(),
                member.hp(),
                member.maxHp(),
                0,
                member.weaponId(),
                member.maxEnergy(),
                0,
                member.energy()
            )
        ).toList();
        var captains = snapshot.captains();
        var leaders = captains == null ? List.of(255, 255, 255)
            : List.of(255, captains.blueLeaderSlot(), captains.redLeaderSlot());
        roots.add(
            new StartGameCodec().encode(
                new PcGameSessionStartGame(
                    snapshot.definitions().map().mapId(),
                    players,
                    snapshot.initialWindRaw(),
                    0,
                    leaders,
                    0
                )
            )
        );
        return output.send(roots);
    }

    @Override
    public CompletionStage<Void> firstTurn(MatchSnapshot snapshot, UUID recipient) {
        var roots = new ArrayList<byte[]>();
        var codec = new SetPosCodec();

        for (var member : snapshot.participants()) {
            if (!member.sessionId().equals(recipient)) {
                var placement = snapshot.actors().get(member.sessionId());
                roots.add(
                    codec.encode(
                        new PcGamePlaySetPos(member.slotIndex(), placement.x(), placement.y(), member.facing())
                    )
                );
            }
        }

        var turn = snapshot.turn();
        roots.add(
            new TurnStartCodec().encode(
                new PcGamePlayTurnStart(
                    turn.activeSlot(),
                    0,
                    turn.windRaw(),
                    turn.energyGrant(),
                    turn.boxAttemptBudget(),
                    turn.pickerBase(),
                    0,
                    0,
                    0,
                    0
                )
            )
        );
        return output.send(roots);
    }

    @Override
    public CompletionStage<Void> end(MatchOutcome outcome) {
        if (!outcome.winningTeams().isEmpty() || outcome.awardsGranted()) {
            throw new IllegalArgumentException("Unsupported match outcome");
        }

        var result = new GameResult(
            0,
            IntStream.range(0, 8).mapToObj(
                index -> new GameResultSlot(index, 0, 0, 0, 0, 0, 0, 0, List.of(0, 0, 0), 0, 0, 0)
            ).toList(),
            0,
            0
        );
        return output.send(List.of(new EndGameCodec().encode(new PcGameSessionEndGame(result, 0))));
    }

    @Override
    public void close(String reason) {
        output.close(reason);
    }
}
