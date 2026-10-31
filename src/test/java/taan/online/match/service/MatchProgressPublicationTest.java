package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.ExperienceTestFixtures;
import taan.online.FixedRewardRandom;
import taan.online.LogCapture;
import taan.online.RoomScenario;
import taan.online.TestLevels;
import taan.online.channel.service.SelectedCharacterAccess;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterProgress;
import taan.online.character.model.CharacterProgressSnapshot;
import taan.online.match.model.MatchOutcome;
import taan.online.match.model.MatchRewardPolicies;
import taan.online.match.model.MatchRewardRules;
import taan.online.match.repository.MatchSettlement;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.room.service.model.RoomMatchCharacterProgress;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.session.model.SessionId;

class MatchProgressPublicationTest {
    @Test
    void updatesOnlyTheCurrentSessionRoomAdmissionAndChannelAfterCommit() {
        try (var f = new MatchOutcomeFixture()) {
            var original = f.snapshot().participants().getFirst();
            var id = original.sessionId();
            var progress = new CharacterProgressSnapshot(
                original.character().id(),
                2,
                new CharacterProgress(
                    1016,
                    1,
                    1,
                    0
                )
            );
            var update = new RoomMatchCharacterProgress(id, original.admissionId(), progress, 123L);
            var callbacks = f.room.scenario.rooms.matchCallbacks();
            callbacks.progressCommitted(f.token, List.of(update));

            assertEquals(2, f.room.scenario.sessions.channelParticipant(new SessionId(id)).orElseThrow().level());
            assertEquals(2, f.room.scenario.channels.membership(id).orElseThrow().participant().level());
            var character = f.room.scenario.rooms.current(id).orElseThrow().slots().stream()
                .filter(slot -> slot.participant() != null && slot.participant().sessionId().equals(id))
                .findFirst().orElseThrow().participant().character();
            assertEquals(progress.progress(), character.progress());
            assertEquals(123, character.gold());
            assertEquals(2, character.level());
            assertEquals(1, original.character().level());
            var stale = new CharacterProgressSnapshot(
                original.character().id(),
                3,
                new CharacterProgress(
                    2650,
                    0,
                    0,
                    0
                )
            );
            callbacks.progressCommitted(
                new RoomMatchToken(f.token.roomId(), f.token.generation(), UUID.randomUUID()),
                List.of(new RoomMatchCharacterProgress(id, original.admissionId(), stale))
            );
            callbacks.progressCommitted(
                f.token,
                List.of(
                    new RoomMatchCharacterProgress(id, UUID.randomUUID(), stale),
                    new RoomMatchCharacterProgress(UUID.randomUUID(), original.admissionId(), stale),
                    new RoomMatchCharacterProgress(
                        id,
                        original.admissionId(),
                        new CharacterProgressSnapshot(
                            new CharacterId(4294967295L),
                            3,
                            new CharacterProgress(2650, 0, 0, 0)
                        )
                    )
                )
            );

            assertEquals(2, f.room.scenario.sessions.channelParticipant(new SessionId(id)).orElseThrow().level());
            assertFalse(f.room.scenario.sessions.refreshProgress(
                new SessionId(id),
                new CharacterProgressSnapshot(
                    new CharacterId(4294967295L),
                    3,
                    new CharacterProgress(2650, 0, 0, 0)
                )
            ));
            assertFalse(f.room.scenario.channels.refreshProgress(UUID.randomUUID(), stale));
            assertFalse(f.room.scenario.channels.refreshProgress(
                id,
                new CharacterProgressSnapshot(
                    new CharacterId(4294967295L),
                    3,
                    new CharacterProgress(2650, 0, 0, 0)
                )
            ));
            assertFalse(f.room.scenario.sessions.refreshProgress(new SessionId(UUID.randomUUID()), stale));
            assertFalse(f.room.scenario.sessions.refreshProgress(f.room.scenario.sessions.open(), stale));
            SelectedCharacterAccess unsupported = session -> Optional.empty();
            assertFalse(unsupported.refreshProgress(id, stale));
        }
    }

    @Test
    void publishesTheCommittedLevelBeforeEndGameAndPreservesItForTheNextRoomSnapshot() throws Exception {
        var action = new SettleMatchResultAction(
            (input, policy) -> new MatchSettlement(ExperienceTestFixtures.settle(input, 939), true),
            Runnable::run,
            Clock.systemUTC(),
            new MatchRewardPolicies(TestLevels.table())
        );

        try (var f = new MatchOutcomeFixture(new RoomScenario(MatchExecutionMode.CLIENT_SCENARIO, action))) {
            var shooter = f.active();
            f.room.scenario.matchTimers.now += 1000000000L;
            f.shot(10000, false, false);
            f.room.scenario.matchTimers.now += 1000000000L;
            f.owner.onEnd = () -> assertEquals(
                2,
                f.room.scenario.sessions.channelParticipant(new SessionId(shooter)).orElseThrow().level()
            );
            f.ack(f.room.owner);
            f.ack(f.room.players.get(1));

            assertEquals(1, f.owner.ends);
            assertEquals(2, f.room.scenario.channels.membership(shooter).orElseThrow().participant().level());
            var saved = f.owner.results.getFirst().result().participants().stream()
                .filter(p -> p.forceMedal()).findFirst().orElseThrow().experienceReward();
            assertEquals(2069, saved.after().progress().experience());
            assertEquals(2, saved.after().level());
        }
    }

    @Test
    void freezesRandomDrawsBeforeRetriesAndLogsCommittedGoldWithoutSensitiveValues() {
        var random = new FixedRewardRandom(99);
        var rules = new MatchRewardRules(TestLevels.table());
        var action = new SettleMatchResultAction(
            (input, policy) -> new MatchSettlement(
                input.rewarded(input.participants().stream().map(p -> rules.calculate(
                    input,
                    p,
                    new CharacterProgress(900, 0, 0, 0),
                    123
                )).toList()),
                true
            ),
            Runnable::run,
            Clock.systemUTC(),
            random,
            new MatchRewardPolicies(TestLevels.table())
        );

        try (var fixture = new MatchOutcomeFixture(); var logs = new LogCapture()) {
            var snapshot = fixture.snapshot();
            var shooter = snapshot.participants().getFirst();
            var prepared = action.prepare(
                snapshot,
                new MatchOutcome(Set.of(shooter.team()), false),
                ExperienceTestFixtures.participation(shooter.sessionId())
            );
            int draws = random.calls;
            var first = action.execute(prepared).toCompletableFuture().join();
            var replay = action.execute(prepared).toCompletableFuture().join();

            assertEquals(1, draws);
            assertEquals(draws, random.calls);
            assertEquals(first, replay);
            assertTrue(logs.text().contains("goldBefore=\"123\""));
            assertTrue(logs.text().contains("cardKind=\"EXPERIENCE\""));
            assertTrue(logs.text().contains("cardAmount=\"50\""));
            assertFalse(logs.text().contains(shooter.character().name().value()));
        }
    }

    @Test
    void logsOnlyCommittedProgressAndReturnsTheSameSavedRewardOnDuplicateSettlement() {
        var result = ExperienceTestFixtures.settle(ExperienceTestFixtures.result(), 900);

        try (var logs = new LogCapture()) {
            for (boolean created : new boolean[]{true, false}) {
                var action = new SettleMatchResultAction(
                    (input, policy) -> new MatchSettlement(result, created),
                    Runnable::run,
                    Clock.systemUTC(),
                    new MatchRewardPolicies(TestLevels.table())
                );
                assertEquals(result, action.execute(result).toCompletableFuture().join());
            }

            assertTrue(logs.text().contains("experienceBefore=\"900\""));
            assertTrue(logs.text().contains("experienceAfter=\"2060\""));
            assertTrue(logs.text().contains("experienceDelta=\"1160\""));
            assertTrue(logs.text().contains("levelBefore=\"1\""));
            assertTrue(logs.text().contains("levelAfter=\"2\""));
            assertTrue(logs.text().contains("killMedal=\"true\""));
            assertTrue(logs.text().contains("match.rewards.duplicate"));
        }
    }
}
