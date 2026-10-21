package taan.online.match.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;
import taan.online.character.controller.CharacterAppearanceMapper;
import taan.online.character.model.CharacterLevelTable;
import taan.online.character.protocol.codec.UpdateExpCodec;
import taan.online.character.protocol.codec.UpdateMoneyCodec;
import taan.online.character.protocol.packet.PcUpdateExp;
import taan.online.character.protocol.packet.PcUpdateMoney;
import taan.online.match.model.MatchExitKind;
import taan.online.match.model.MatchParticipantResult;
import taan.online.match.model.MatchRewardCardKind;
import taan.online.match.protocol.codec.EatBonusItemCodec;
import taan.online.match.protocol.codec.EndGameCodec;
import taan.online.match.protocol.codec.ForsakeItemCodec;
import taan.online.match.protocol.codec.LeaveUserCodec;
import taan.online.match.protocol.codec.MoveCodec;
import taan.online.match.protocol.codec.PassTurnCodec;
import taan.online.match.protocol.codec.ResultSelectionCodec;
import taan.online.match.protocol.codec.SetPosCodec;
import taan.online.match.protocol.codec.SetRewardCodec;
import taan.online.match.protocol.codec.SpendItemCodec;
import taan.online.match.protocol.codec.StartGameCodec;
import taan.online.match.protocol.codec.TurnEndCodec;
import taan.online.match.protocol.codec.TurnStartCodec;
import taan.online.match.protocol.codec.UseItemCodec;
import taan.online.match.protocol.packet.ExpResult;
import taan.online.match.protocol.packet.GameResult;
import taan.online.match.protocol.packet.GameResultSlot;
import taan.online.match.protocol.packet.GameReward;
import taan.online.match.protocol.packet.GameRewardExp;
import taan.online.match.protocol.packet.GameRewardGold;
import taan.online.match.protocol.packet.GameRewardQuest;
import taan.online.match.protocol.packet.GameSlotPlayInfo;
import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;
import taan.online.match.protocol.packet.PcGamePlayForsakeItem;
import taan.online.match.protocol.packet.PcGamePlayLeaveUser;
import taan.online.match.protocol.packet.PcGamePlayMove;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.match.protocol.packet.PcGamePlaySetPos;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.protocol.packet.PcGamePlayTurnEnd;
import taan.online.match.protocol.packet.PcGamePlayTurnStart;
import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.match.protocol.packet.PcGameSessionEndGame;
import taan.online.match.protocol.packet.PcGameSessionStartGame;
import taan.online.match.protocol.packet.PcResultSetReward;
import taan.online.match.protocol.packet.PrResultAlreadyOpend;
import taan.online.match.protocol.packet.PrResultSelectCard;
import taan.online.match.protocol.packet.ResultBonus;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.SpentItem;
import taan.online.match.service.MatchOutput;
import taan.online.match.service.model.CommittedScenario;
import taan.online.match.service.model.DiscardMatchItemCommand;
import taan.online.match.service.model.MatchBonusDelivery;
import taan.online.match.service.model.MatchEndResult;
import taan.online.match.service.model.MatchMovementNotice;
import taan.online.match.service.model.MatchRewardSelectionNotice;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.MatchTurnTransition;
import taan.online.match.service.model.PendingMatchSpend;
import taan.online.match.service.model.UseMatchItemCommand;
import taan.online.protocol.PacketOutput;
import taan.online.protocol.option.packet.OptionList;
import taan.online.room.model.RoomSlotState;
import taan.online.room.model.RoomTeam;
import taan.online.room.protocol.codec.RoomResponseCodec;
import taan.online.room.protocol.packet.GameSlotInfo;
import taan.online.room.protocol.packet.GameSlotOccupant;
import taan.online.room.protocol.packet.PcGameSessionUpdateSlot;

public final class ProtocolMatchView implements MatchOutput {
    private final CharacterLevelTable levels;

    private final PacketOutput output;

    public ProtocolMatchView(PacketOutput output, CharacterLevelTable levels) {
        this.levels = levels;
        this.output = output;
    }

    @Override
    public CompletionStage<Void> start(MatchSnapshot snapshot) {
        var roots = new ArrayList<byte[]>();
        var roomCodec = new RoomResponseCodec();
        var appearance = new CharacterAppearanceMapper();

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
            if (snapshot.resolution().exits().get(member.sessionId()) == MatchExitKind.DEPARTED) {
                roots.add(new LeaveUserCodec().encode(new PcGamePlayLeaveUser(member.slotIndex())));
            } else if (!member.sessionId().equals(recipient)) {
                var placement = snapshot.actors().get(member.sessionId());
                roots.add(
                    codec.encode(
                        new PcGamePlaySetPos(member.slotIndex(), placement.x(), placement.y(), placement.facing())
                    )
                );
            }
        }

        roots.add(turnStart(snapshot));
        return output.send(roots);
    }

    @Override
    public CompletionStage<Void> nextTurn(MatchTurnTransition transition) {
        var roots = new ArrayList<byte[]>();

        if (transition.turnEndRequired()) {
            roots.add(new TurnEndCodec().encode(new PcGamePlayTurnEnd(transition.previousSlot(), 0)));
        }

        roots.add(turnStart(transition.snapshot()));
        return output.send(roots);
    }

    @Override
    public CompletionStage<Void> movement(MatchMovementNotice notice) {
        byte[] root = switch (notice.kind()) {
            case MOVE -> new MoveCodec().encode(new PcGamePlayMove(
                notice.slot(),
                notice.x(),
                notice.y(),
                notice.facing()
            ));
            case SET_POS -> new SetPosCodec().encode(new PcGamePlaySetPos(
                notice.slot(),
                notice.x(),
                notice.y(),
                notice.facing()
            ));
            case PASS_TURN -> new PassTurnCodec().encode(new PcGamePlayPassTurn(
                notice.slot(),
                notice.x(),
                notice.y(),
                notice.facing(),
                notice.scalar20()
            ));
        };
        return output.send(List.of(root));
    }

    @Override
    public CompletionStage<Void> stopTurn(int slot) {
        return output.send(List.of(new TurnEndCodec().encode(new PcGamePlayTurnEnd(slot, 0))));
    }

    private byte[] turnStart(MatchSnapshot snapshot) {
        var turn = snapshot.turn();
        return new TurnStartCodec().encode(new PcGamePlayTurnStart(
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
        ));
    }

    @Override
    public CompletionStage<Void> useItem(UseMatchItemCommand command) {
        var packet = new PcGamePlayUseItem(
            command.slot(),
            command.team(),
            command.flag12(),
            command.flag13(),
            command.inventoryIndex(),
            command.itemId()
        );
        return output.send(List.of(new UseItemCodec().encode(packet)));
    }

    @Override
    public CompletionStage<Void> spend(PendingMatchSpend spend) {
        var items = spend.items().stream().map(item -> new SpentItem(item.itemId(), item.scalar2())).toList();
        return output.send(List.of(new SpendItemCodec().encode(new PcGamePlaySpendItem(
            spend.slot(),
            spend.teamClear11(),
            spend.teamClear12(),
            items
        ))));
    }

    @Override
    public CompletionStage<Void> bonus(MatchBonusDelivery bonus) {
        var command = new ScenarioEatItemBoxCommand(
            new ScenarioCommandHeader(bonus.slotIndex(), 11, bonus.tick(), bonus.scalar10()),
            -1L,
            bonus.itemId(),
            bonus.opaque24()
        );
        var packet = new PcGamePlayEatBonusItem(bonus.slotIndex(), command, Optional.empty());
        return output.send(List.of(new EatBonusItemCodec().encode(packet)));
    }

    @Override
    public CompletionStage<Void> discard(DiscardMatchItemCommand discard) {
        var packet = new PcGamePlayForsakeItem(
            discard.slotIndex(),
            discard.inventoryIndex(),
            discard.itemId()
        );
        return output.send(List.of(new ForsakeItemCodec().encode(packet)));
    }

    @Override
    public CompletionStage<Void> scenario(CommittedScenario scenario) {
        return output.send(List.of(scenario.scenario().root()));
    }

    @Override
    public CompletionStage<Void> end(MatchEndResult end) {
        int winner = end.result().winningTeam().map(team -> team == RoomTeam.BLUE ? 1 : 2).orElse(0);
        var result = new GameResult(
            winner,
            IntStream.range(0, 8).mapToObj(index -> resultSlot(end, index)).toList(),
            0,
            0
        );
        var rewards = IntStream.range(0, 8).mapToObj(index -> rewardCard(end, index)).toList();
        var roots = new ArrayList<byte[]>();
        end.participants().stream().filter(p -> p.sessionId().equals(end.recipientSessionId()))
            .flatMap(p -> end.result().participants().stream().filter(v -> v.characterId().equals(p.character().id())))
            .map(MatchParticipantResult::experienceReward).filter(Objects::nonNull).findFirst()
            .ifPresent(reward -> roots.add(new UpdateExpCodec().encode(new PcUpdateExp(
                0,
                reward.after().level(),
                reward.after().progress().experience(),
                levels.displayStart(reward.after().level()),
                levels.displayNext(reward.after().level())
            ))));
        end.participants().stream().filter(p -> p.sessionId().equals(end.recipientSessionId()))
            .flatMap(p -> end.result().participants().stream().filter(v -> v.characterId().equals(p.character().id())))
            .map(MatchParticipantResult::goldReward).filter(Objects::nonNull).findFirst()
            .ifPresent(gold -> roots.add(new UpdateMoneyCodec().encode(new PcUpdateMoney(0, 0, gold.after(), 0, 0))));
        roots.add(new SetRewardCodec().encode(new PcResultSetReward(rewards)));
        roots.add(new EndGameCodec().encode(new PcGameSessionEndGame(result, 0)));
        return output.send(roots);
    }

    private GameResultSlot resultSlot(MatchEndResult end, int index) {
        var participant = end.result().participants().stream().filter(value -> value.slotIndex() == index).findFirst();

        if (participant.isEmpty()) {
            return new GameResultSlot(index, 0, 0, 0, 0, 0, 0, 0, List.of(0, 0, 0), 0, 0, 0);
        }

        MatchParticipantResult statistics = participant.orElseThrow();
        int level = end.participants().stream().filter(value -> value.character().id().equals(statistics.characterId()))
            .findFirst().orElseThrow().character().level();
        var reward = statistics.experienceReward();
        var snapshots = reward == null ? List.<ExpResult>of() : List.of(
            experience(reward.levelBefore(), reward.experienceBefore()),
            experience(reward.after().level(), reward.after().progress().experience())
        );
        var bonuses = reward == null ? List.<ResultBonus>of() : List.of(
            new ResultBonus(0, Math.toIntExact(reward.pcroomDelta()), 0),
            new ResultBonus(
                4,
                Math.toIntExact(reward.medalDelta()),
                statistics.goldReward() == null ? 0 : Math.toIntExact(statistics.goldReward().medalDelta())
            )
        );
        return new GameResultSlot(
            index,
            reward == null ? level : reward.after().level(),
            reward == null ? 0 : Integer.toUnsignedLong(Math.toIntExact(reward.delta())),
            statistics.goldReward() == null ? 0 : statistics.goldReward().delta(),
            statistics.kills(),
            statistics.damage(),
            statistics.shots() == null ? 0 : statistics.shots().accuracy(),
            0,
            List.of(statistics.forceMedal() ? 1 : 0, statistics.killMedal() ? 1 : 0, 0),
            0,
            0,
            0,
            snapshots,
            bonuses
        );
    }

    private GameReward rewardCard(MatchEndResult end, int index) {
        var participant = end.result().participants().stream().filter(p -> p.slotIndex() == index).findFirst();

        if (participant.isEmpty()) {
            return new GameRewardQuest("", 0, 0);
        }

        var member = participant.orElseThrow();
        var card = member.card();

        if (card == null) {
            return new GameRewardQuest("", member.characterId().value(), 0);
        }

        return card.kind() == MatchRewardCardKind.EXPERIENCE
            ? new GameRewardExp("", member.characterId().value(), card.amount())
            : new GameRewardGold("", member.characterId().value(), card.amount(), 0, 0);
    }

    @Override
    public CompletionStage<Void> selectReward(MatchRewardSelectionNotice notice) {
        var codec = new ResultSelectionCodec();
        byte[] packet = notice.alreadyOpen()
            ? codec.encode(new PrResultAlreadyOpend(notice.cardIndex(), notice.slotIndex()))
            : codec.encode(new PrResultSelectCard(notice.cardIndex(), notice.slotIndex()));
        return output.send(List.of(packet));
    }

    private ExpResult experience(int level, long current) {
        return new ExpResult(level, current, levels.displayStart(level), levels.displayNext(level));
    }

    @Override
    public void close(String reason) {
        output.close(reason);
    }
}
