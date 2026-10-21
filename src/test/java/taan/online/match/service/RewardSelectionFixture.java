package taan.online.match.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import org.slf4j.LoggerFactory;
import taan.online.FixedRewardRandom;
import taan.online.MatchBootstrapFixture;
import taan.online.logging.WarningLogLimiter;
import taan.online.match.service.model.SelectMatchRewardCommand;
import taan.online.room.model.RoomMode;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchTerminationMode;
import taan.online.room.service.model.RoomMembership;

final class RewardSelectionFixture implements AutoCloseable {
    final MatchBootstrapFixture fixture;
    final ArrayDeque<Runnable> work = new ArrayDeque<>();
    final MatchResultSelections selections;
    final List<RoomMembership> members;
    final RoomMatchTermination terminal;
    final List<MatchSubscription> outputs;

    RewardSelectionFixture(int count, int capacity, boolean queued) {
        fixture = new MatchBootstrapFixture(count, RoomMode.ITEM);
        fixture.start();
        var snapshot = fixture.snapshot();
        members = snapshot.participants().stream().map(p -> new RoomMembership(
            p.sessionId(),
            snapshot.roomId(),
            snapshot.roomGeneration(),
            snapshot.channelId(),
            p.admissionId()
        )).toList();
        terminal = new RoomMatchTermination(
            fixture.token(),
            RoomMatchEndReason.PARTICIPANT_ELIMINATED,
            RoomMatchTerminationMode.RETURN_TO_ROOM,
            members
        );
        outputs = snapshot.participants().stream().map(p -> new MatchSubscription(
            p.sessionId(),
            fixture.outputs.get(fixture.room.players.indexOf(p.sessionId()))
        )).toList();
        selections = new MatchResultSelections(
            queued ? work::addLast : Runnable::run,
            capacity,
            new FixedRewardRandom(0),
            new WarningLogLimiter(
                LoggerFactory.getLogger(RewardSelectionFixture.class),
                1,
                Duration.ofSeconds(1),
                System::nanoTime
            )
        );
        selections.reserve(terminal.token(), members);
        selections.publish(terminal, snapshot.participants(), outputs);
    }

    SelectMatchRewardCommand command(int index, Integer card) {
        var member = members.get(index);
        int slot = fixture.snapshot().participants().stream().filter(p -> p.sessionId().equals(member.sessionId()))
            .findFirst().orElseThrow().slotIndex();
        return new SelectMatchRewardCommand(
            member.sessionId(),
            new RoomMatchContext(
                member,
                terminal.token()
            ),
            slot,
            card
        );
    }

    TestMatchOutput output(int index) {
        return (TestMatchOutput) outputs.get(index).output();
    }

    @Override
    public void close() {
        selections.cancel(terminal.token());

        while (!work.isEmpty()) {
            work.removeFirst().run();
        }

        fixture.close();
    }
}
