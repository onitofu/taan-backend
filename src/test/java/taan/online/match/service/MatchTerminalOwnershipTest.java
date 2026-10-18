package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.MatchTerminalFixture;
import taan.online.LogCapture;
import taan.online.match.model.MatchPhase;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomState;
import taan.online.session.model.SessionId;
import taan.online.room.service.model.RoomMatchContext;
import taan.online.room.service.model.RoomMatchEndReason;
import taan.online.room.service.model.RoomMatchTermination;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;

class MatchTerminalOwnershipTest {
    @Test
    void dispatchesOnlyThePreparedTerminalOnceAndNeverCallsOutputsUnderTheMatchLock() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var matches = MatchFixtures.registry();
            var draft = f.draft();
            var token = draft.token();
            matches.reserve(draft);
            assertEquals(MatchPhase.RESERVED, matches.current(token).orElseThrow().phase());
            matches.commit(token);
            MatchFixtures.loaded(matches, token);
            assertEquals(MatchPhase.PREVIEW, matches.current(token).orElseThrow().phase());
            var output = new TestMatchOutput();
            output.onEnd = () -> assertFalse(Thread.holdsLock(matches));
            matches.subscribe(f.owner, output);
            matches.subscribe(f.owner, new TestMatchOutput());
            var terminal = matches.prepareTermination(token, RoomMatchEndReason.GIVE_UP, null);
            var forged = new RoomMatchTermination(
                token,
                RoomMatchEndReason.PREVIEW_TIMEOUT,
                terminal.mode(),
                terminal.recipients()
            );
            matches.dispatchTermination(forged, f.owner);

            assertEquals(0, output.ends);
            matches.dispatchTermination(terminal, f.owner);
            matches.dispatchTermination(terminal, f.owner);

            assertEquals(1, output.ends);
            assertNull(matches.prepareTermination(token, RoomMatchEndReason.GIVE_UP, null));
            assertTrue(matches.current(token).isEmpty());
            var member = draft.participants().getFirst().membership();
            var otherAdmission = new RoomMembership(
                member.sessionId(),
                member.roomId(),
                member.generation(),
                member.channelId(),
                UUID.randomUUID()
            );
            matches.release(otherAdmission);

            assertNull(matches.resultToken(otherAdmission));
            assertEquals(token, matches.resultToken(member));
            assertFalse(
                matches.consumeResult(
                    new RoomMatchContext(
                        member,
                        new RoomMatchToken(token.roomId(), token.generation(), UUID.randomUUID())
                    )
                )
            );
            matches.release(member);
            matches.release(member);

            assertNull(matches.resultToken(member));
            assertEquals(token, matches.resultToken(draft.participants().get(1).membership()));
            matches.cancel(token, "STALE");

            assertNull(matches.resultToken(draft.participants().get(1).membership()));
        }
    }

    @Test
    void rejectsStaleCallbacksAndClosesAnOrphanedRoomBindingInsteadOfLeavingItLocked() {
        try (var f = new MatchTerminalFixture(2, RoomMode.ITEM, MatchPhase.PREVIEW); var logs = new LogCapture()) {
            var rooms = f.room.scenario.rooms;
            var context = rooms.matchContext(f.room.owner);
            var token = f.token;
            assertEquals(token, rooms.matchContext(f.room.owner).token());
            assertNull(
                rooms.terminateMatch(
                    new RoomMatchToken(token.roomId(), UUID.randomUUID(), token.matchId()),
                    RoomMatchEndReason.LOADING_TIMEOUT
                ).termination()
            );
            var wrongMember = new RoomMembership(
                f.room.owner,
                token.roomId(),
                token.generation(),
                1,
                UUID.randomUUID()
            );
            assertNull(
                rooms.terminateMatch(new RoomMatchContext(wrongMember, token), RoomMatchEndReason.GIVE_UP)
                    .termination()
            );
            f.room.scenario.matches.cancel(token, "FAULT_INJECTION");
            assertNull(rooms.terminateMatch(context, RoomMatchEndReason.GIVE_UP).termination());

            assertEquals(RoomState.WAITING, rooms.current(f.room.owner).orElseThrow().state());
            assertTrue(f.room.outputs.stream().allMatch(output -> "MATCH_CONTEXT_LOST".equals(output.closed)));
            assertTrue(logs.text().contains("room.operation.failed"));
            assertEquals(
                1,
                logs.events().stream().filter(event -> event.getLevel().toString().equals("ERROR")).count()
            );
        }
    }

    @Test
    void serializesCompetingTerminalCausesAndDiscardsOldAdmissions() {
        try (var f = new MatchTerminalFixture(2, RoomMode.ITEM, MatchPhase.PREVIEW)) {
            var rooms = f.room.scenario.rooms;
            var context = rooms.matchContext(f.room.owner);
            var first = CompletableFuture.runAsync(
                () -> rooms.terminateMatch(f.token, RoomMatchEndReason.PREVIEW_TIMEOUT)
            );
            var second = CompletableFuture.runAsync(() -> rooms.terminateMatch(context, RoomMatchEndReason.GIVE_UP));
            CompletableFuture.allOf(first, second).join();

            assertTrue(f.outputs.stream().allMatch(output -> output.ends == 1));
            assertTrue(f.room.scenario.matches.current(f.token).isEmpty());
            rooms.release(f.room.owner, "PEER_CLOSED");

            assertNull(rooms.matchContext(f.room.owner));
            assertNull(rooms.terminateMatch(context, RoomMatchEndReason.GIVE_UP).termination());
            assertFalse(f.room.scenario.matches.consumeResult(context));
        }
    }

    @Test
    void rejectsAClosedPrincipalBeforeConfirmingPreview() {
        try (var f = new MatchTerminalFixture(2, RoomMode.ITEM, MatchPhase.LOADING)) {
            f.room.scenario.sessions.close(new SessionId(f.room.owner));

            assertNull(f.room.scenario.rooms.matchContext(f.room.owner));
            assertTrue(
                f.room.scenario.rooms.matchAccess().loadingDone(
                    new RoomMatchContext(f.room.scenario.rooms.membership(f.room.owner).orElseThrow(), f.token),
                    0,
                    97,
                    685
                ).toCompletableFuture().isDone()
            );
            assertEquals(MatchPhase.LOADING, f.room.scenario.matches.current(f.token).orElseThrow().phase());
        }
    }

    @Test
    void repairsAnOrphanedRollbackAndKeepsNewResultReceiptsWhenAnOldTokenIsCancelled() {
        try (var f = new MatchTerminalFixture(2, RoomMode.ITEM, MatchPhase.RESERVED); var logs = new LogCapture()) {
            f.room.scenario.matches.cancel(f.token, "FAULT_INJECTION");
            assertEquals("STALE_MATCH", f.room.scenario.rooms.rollbackMatch(f.token, "STALE").reason());
            assertEquals(RoomState.WAITING, f.room.scenario.rooms.current(f.room.owner).orElseThrow().state());
            assertTrue(logs.text().contains("room.operation.failed"));
        }

        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var matches = MatchFixtures.registry();
            var draft = f.draft();
            matches.reserve(draft);
            matches.commit(draft.token());
            MatchFixtures.loaded(matches, draft.token());
            var old = matches.prepareTermination(draft.token(), RoomMatchEndReason.GIVE_UP, null);
            var next = f.draft();
            matches.reserve(next);
            matches.commit(next.token());
            MatchFixtures.loaded(matches, next.token());
            var terminal = matches.prepareTermination(next.token(), RoomMatchEndReason.GIVE_UP, null);
            matches.cancel(old.token(), "STALE_CALLBACK");

            assertTrue(matches.dispatchTermination(old, null).toCompletableFuture().isDone());
            assertEquals(next.token(), matches.resultToken(draft.participants().getFirst().membership()));
            matches.dispatchTermination(terminal, null);
            matches.unsubscribe(f.owner);

            assertNull(matches.resultToken(draft.participants().getFirst().membership()));
        }
    }

    @Test
    void rejectsMismatchedRoomIdentityAndGenerationInCapturedContexts() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var draft = f.draft();
            var member = draft.participants().getFirst().membership();
            assertThrows(
                IllegalArgumentException.class,
                () -> new RoomMatchContext(
                    member,
                    new RoomMatchToken(new RoomId(999), member.generation(), UUID.randomUUID())
                )
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> new RoomMatchContext(
                    member,
                    new RoomMatchToken(member.roomId(), UUID.randomUUID(), UUID.randomUUID())
                )
            );
        }
    }
}
