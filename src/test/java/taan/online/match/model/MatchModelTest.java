package taan.online.match.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.character.model.GameCharacter;
import taan.online.room.model.CaptainSelectionResult;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomRule;
import taan.online.room.model.RoomTeam;

class MatchModelTest {
    @Test
    void validatesActorSlotsWeaponsStatsSidesAndOrientation() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var token = f.scenario.rooms.reserveMatch(f.owner).token();
            var p = f.scenario.matches.current(token).orElseThrow().participants().getFirst();

            for (int[] values : List.of(
                new int[]{-1, 0, 0, 1000, 1000, 0, 100},
                new int[]{8, 0, 0, 1000, 1000, 0, 100},
                new int[]{0, 4, 0, 1000, 1000, 0, 100},
                new int[]{0, 0, -1, 1000, 1000, 0, 100},
                new int[]{0, 0, 2, 1000, 1000, 0, 100},
                new int[]{0, 0, 0, 0, 1000, 0, 100},
                new int[]{0, 0, 0, 1001, 1000, 0, 100},
                new int[]{0, 0, 0, 1000, 1000, -1, 100},
                new int[]{0, 0, 0, 1000, 1000, 101, 100}
            )) {
                assertThrows(
                    IllegalArgumentException.class,
                    () -> new MatchParticipant(
                        p.sessionId(),
                        p.admissionId(),
                        values[0],
                        p.team(),
                        p.character(),
                        values[1],
                        p.spawn(),
                        values[2],
                        values[3],
                        values[4],
                        values[5],
                        values[6],
                        p.roomSlotState()
                    )
                );
            }

            assertThrows(
                IllegalArgumentException.class,
                () -> copy(p, p.sessionId(), p.admissionId(), 2, RoomTeam.BLUE, p.character(), p.spawn())
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> copy(
                    p,
                    p.sessionId(),
                    p.admissionId(),
                    0,
                    RoomTeam.BLUE,
                    p.character(),
                    new MapSpawnDefinition(0, 1, 1, 1, 1, 1, false)
                )
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> copy(
                    p,
                    p.sessionId(),
                    p.admissionId(),
                    0,
                    RoomTeam.BLUE,
                    p.character(),
                    new MapSpawnDefinition(0, 1, 1, 1, 1, 2, true)
                )
            );
            assertEquals(
                0,
                copy(
                    p,
                    p.sessionId(),
                    p.admissionId(),
                    0,
                    RoomTeam.BLUE,
                    p.character(),
                    new MapSpawnDefinition(0, 1, 1, 1, 1, 0, true)
                ).spawn().side()
            );
        }
    }

    @Test
    void rejectsDuplicateIdentitiesSlotsSpawnsAndForeignDefinitions() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var token = f.scenario.rooms.reserveMatch(f.owner).token();
            var snapshot = f.scenario.matches.current(token).orElseThrow();
            var a = snapshot.participants().get(0);
            var b = snapshot.participants().get(1);
            var definitions = snapshot.definitions();
            var duplicateAccount = CharacterFixtures.character(
                b.character().id().value(),
                a.character().accountId(),
                b.character().gender(),
                "Other"
            );
            var duplicateCharacter = CharacterFixtures.character(
                a.character().id().value(),
                b.character().accountId(),
                b.character().gender(),
                "Other"
            );

            for (var invalid : List.of(
                copy(b, a.sessionId(), b.admissionId(), 2, RoomTeam.RED, b.character(), b.spawn()),
                copy(b, b.sessionId(), a.admissionId(), 2, RoomTeam.RED, b.character(), b.spawn()),
                copy(b, b.sessionId(), b.admissionId(), 2, RoomTeam.RED, duplicateAccount, b.spawn()),
                copy(b, b.sessionId(), b.admissionId(), 2, RoomTeam.RED, duplicateCharacter, b.spawn()),
                copy(
                    b,
                    b.sessionId(),
                    b.admissionId(),
                    0,
                    RoomTeam.BLUE,
                    b.character(),
                    definitions.map().spawns().get(1)
                ),
                copy(
                    b,
                    b.sessionId(),
                    b.admissionId(),
                    2,
                    RoomTeam.RED,
                    b.character(),
                    new MapSpawnDefinition(99, 1, 1, 1, 1, 2, true)
                )
            )) {
                assertThrows(
                    IllegalArgumentException.class,
                    () -> match(List.of(a, invalid), f.owner, RoomMode.ITEM, RoomRule.BONUS, 1, null)
                );
            }

            var neutral = definitions.map().spawns().get(5);
            var neutralA = copy(a, a.sessionId(), a.admissionId(), 0, RoomTeam.BLUE, a.character(), neutral);
            var neutralB = copy(b, b.sessionId(), b.admissionId(), 2, RoomTeam.RED, b.character(), neutral);

            assertThrows(
                IllegalArgumentException.class,
                () -> match(List.of(neutralA, neutralB), f.owner, RoomMode.ITEM, RoomRule.BONUS, 1, null)
            );
        }
    }

    @Test
    void validatesOwnershipTeamsModeAndCaptainsAndTransitionsWithoutExposingMutableState() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var token = f.scenario.rooms.reserveMatch(f.owner).token();
            var participants = f.scenario.matches.current(token).orElseThrow().participants();

            for (int channel : new int[]{0, 65536}) {
                assertThrows(
                    IllegalArgumentException.class,
                    () -> match(participants, f.owner, RoomMode.ITEM, RoomRule.BONUS, channel, null)
                );
            }

            for (var roster : List.of(List.<MatchParticipant>of(), Collections.nCopies(9, participants.get(0)))) {
                assertThrows(
                    IllegalArgumentException.class,
                    () -> match(roster, f.owner, RoomMode.ITEM, RoomRule.BONUS, 1, null)
                );
            }

            assertThrows(
                IllegalArgumentException.class,
                () -> match(participants, f.owner, RoomMode.ITEM, RoomRule.BASIC, 1, null)
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> match(participants, UUID.randomUUID(), RoomMode.ITEM, RoomRule.BONUS, 1, null)
            );
            var b = participants.get(1);
            var blueB = copy(
                b,
                b.sessionId(),
                b.admissionId(),
                1,
                RoomTeam.BLUE,
                b.character(),
                MatchFixtures.definitions().map().spawns().get(1)
            );

            assertThrows(
                IllegalArgumentException.class,
                () -> match(List.of(participants.get(0), blueB), f.owner, RoomMode.ITEM, RoomRule.BONUS, 1, null)
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> match(participants, f.owner, RoomMode.CAPTAIN_ITEM, RoomRule.BONUS, 1, null)
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> match(participants, f.owner, RoomMode.ITEM, RoomRule.BONUS, 1, new CaptainSelectionResult(0, 2))
            );

            for (var captains : List.of(new CaptainSelectionResult(1, 2), new CaptainSelectionResult(0, 3))) {
                assertThrows(
                    IllegalArgumentException.class,
                    () -> match(participants, f.owner, RoomMode.CAPTAIN_ITEM, RoomRule.BONUS, 1, captains)
                );
            }

            var mutable = new ArrayList<>(participants);
            var match = match(
                mutable,
                f.owner,
                RoomMode.CAPTAIN_NO_ITEM,
                RoomRule.BASIC,
                1,
                new CaptainSelectionResult(0, 2)
            );
            mutable.clear();

            assertEquals(2, match.participants().size());
            assertThrows(UnsupportedOperationException.class, () -> match.participants().clear());
            assertTrue(match.commit(0, 30_000_000_000L));
            assertFalse(match.commit(0, 30_000_000_000L));
            assertTrue(match.cancel());
            assertFalse(match.cancel());
            assertFalse(match.commit(0, 30_000_000_000L));
            assertEquals(MatchPhase.CLOSED, match.phase());
        }
    }

    private Match match(
        List<MatchParticipant> participants,
        UUID owner,
        RoomMode mode,
        RoomRule rule,
        int channel,
        CaptainSelectionResult captains
    ) {
        return new Match(
            new MatchId(UUID.randomUUID()),
            new RoomId(1),
            UUID.randomUUID(),
            channel,
            owner,
            mode,
            rule,
            MatchFixtures.definitions(),
            participants,
            captains,
            participants.isEmpty() ? 0 : participants.getFirst().slotIndex(),
            MatchBoxPolicy.DISABLED
        );
    }

    private MatchParticipant copy(
        MatchParticipant p,
        UUID session,
        UUID admission,
        int slot,
        RoomTeam team,
        GameCharacter character,
        MapSpawnDefinition spawn
    ) {
        return new MatchParticipant(
            session,
            admission,
            slot,
            team,
            character,
            0,
            spawn,
            p.facing(),
            p.hp(),
            p.maxHp(),
            p.energy(),
            p.maxEnergy(),
            p.roomSlotState()
        );
    }
}
