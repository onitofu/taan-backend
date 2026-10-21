package taan.online.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.room.service.model.RoomMatchReservationOutcome.RESERVED;
import static taan.online.room.service.model.RoomMatchReservationOutcome.STALE;
import static taan.online.room.service.model.RoomMatchReservationOutcome.UNCHANGED;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.LogCapture;
import taan.online.MatchFixtures;
import taan.online.MatchRoomFixture;
import taan.online.account.model.AccountId;
import taan.online.character.model.GameCharacter;
import taan.online.room.model.RoomId;
import taan.online.room.model.RoomMode;
import taan.online.room.model.RoomParticipant;
import taan.online.room.model.RoomSettings;
import taan.online.room.model.RoomSlot;
import taan.online.room.model.RoomSlotState;
import taan.online.room.service.model.RoomMatchDraft;
import taan.online.room.service.model.RoomMatchParticipant;
import taan.online.room.service.model.RoomMatchToken;
import taan.online.room.service.model.RoomMembership;

class MatchRegistryTest {
    @Test
    void makesReservationIdempotentButRejectsConflictingRoomAndMatchIdentities() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var registry = MatchFixtures.registry();
            var draft = f.draft();
            var token = draft.token();

            assertEquals(RESERVED, registry.reserve(draft).outcome());
            assertEquals(UNCHANGED, registry.reserve(draft).outcome());
            assertEquals("ALREADY_RESERVED", registry.reserve(f.draft()).reason());
            assertEquals(
                "ALREADY_RESERVED",
                registry.reserve(
                    new RoomMatchDraft(
                        new RoomMatchToken(new RoomId(2), UUID.randomUUID(), token.matchId()),
                        1,
                        f.owner,
                        draft.settings(),
                        draft.participants(),
                        null,
                        0
                    )
                ).reason()
            );

            for (var stale : List.of(
                new RoomMatchToken(new RoomId(2), token.generation(), token.matchId()),
                new RoomMatchToken(token.roomId(), UUID.randomUUID(), token.matchId()),
                new RoomMatchToken(token.roomId(), token.generation(), UUID.randomUUID())
            )) {
                assertTrue(registry.current(stale).isEmpty());
                assertEquals(STALE, registry.commit(stale).toCompletableFuture().join().outcome());
                assertEquals(STALE, registry.cancel(stale, "STALE").outcome());
            }

            registry.cancel(token, "ROLLBACK");

            assertEquals(STALE, registry.cancel(token, "AGAIN").outcome());
            assertTrue(registry.current(token).isEmpty());
        }
    }

    @Test
    void rejectsUnsupportedWeaponsMapAndForgedAdmissionWithoutCreatingRuntime() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM); var logs = new LogCapture()) {
            var registry = MatchFixtures.registry();
            var draft = f.draft();
            var p = draft.participants().getFirst();
            var member = p.membership();
            var token = draft.token();

            for (var admission : List.of(
                new RoomMembership(member.sessionId(), new RoomId(2), member.generation(), 1, member.admissionId()),
                new RoomMembership(member.sessionId(), member.roomId(), UUID.randomUUID(), 1, member.admissionId()),
                new RoomMembership(member.sessionId(), member.roomId(), member.generation(), 2, member.admissionId())
            )) {
                var invalid = new RoomMatchParticipant(admission, p.slot());

                assertEquals(
                    "ADMISSION_MISMATCH",
                    registry.reserve(
                        new RoomMatchDraft(
                            token,
                            1,
                            f.owner,
                            draft.settings(),
                            List.of(invalid, draft.participants().get(1)),
                            null,
                            0
                        )
                    ).reason()
                );
            }

            var slot = p.slot();
            var invalid = new RoomMatchParticipant(
                member,
                new RoomSlot(
                    slot.index(),
                    slot.state(),
                    new RoomParticipant(member.sessionId(), slot.participant().character(), 200)
                )
            );
            assertEquals(
                "UNSUPPORTED_WEAPON",
                registry.reserve(
                    new RoomMatchDraft(
                        token,
                        1,
                        f.owner,
                        draft.settings(),
                        List.of(invalid, draft.participants().get(1)),
                        null,
                        0
                    )
                ).reason()
            );
            var settings = draft.settings();

            assertEquals(
                "UNSUPPORTED_MAP",
                registry.reserve(
                    new RoomMatchDraft(
                        token,
                        1,
                        f.owner,
                        new RoomSettings(settings.title(), settings.mode(), settings.rule(), 2, 8),
                        draft.participants(),
                        null,
                        0
                    )
                ).reason()
            );
            assertEquals(
                "INVALID_SETUP",
                registry.reserve(new RoomMatchDraft(token, 1, f.owner, settings, List.of(), null, 0)).reason()
            );
            assertTrue(registry.current(token).isEmpty());
            assertTrue(logs.text().contains("match.reservation.rejected"));
            assertFalse(logs.text().contains("Hero"));
            assertThrows(
                IllegalArgumentException.class,
                () -> new RoomMatchParticipant(member, new RoomSlot(0, RoomSlotState.OPEN, null))
            );
            assertThrows(
                IllegalArgumentException.class,
                () -> new RoomMatchParticipant(
                    new RoomMembership(
                        UUID.randomUUID(),
                        member.roomId(),
                        member.generation(),
                        1,
                        member.admissionId()
                    ),
                    slot
                )
            );
            assertThrows(UnsupportedOperationException.class, () -> draft.participants().clear());
        }
    }

    @Test
    void refusesToBindTheSameSessionAccountOrCharacterToAnotherMatch() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var draft = f.draft();
            var registry = MatchFixtures.registry();
            registry.reserve(draft);
            var a = draft.participants().get(0);
            var b = draft.participants().get(1);
            var character = a.slot().participant().character();
            var other = CharacterFixtures.character(999, new AccountId(999), character.gender(), "Other");

            for (int identity = 0; identity < 3; identity++) {
                var token = new RoomMatchToken(new RoomId(2), UUID.randomUUID(), UUID.randomUUID());
                var session = identity == 0 ? a.membership().sessionId() : UUID.randomUUID();
                GameCharacter claimed = identity == 2 ? CharacterFixtures.character(
                    character.id().value(),
                    new AccountId(998),
                    character.gender(),
                    "Other"
                ) : character;
                var first = participant(a, token, session, claimed);
                var second = participant(b, token, UUID.randomUUID(), other);

                assertEquals(
                    "PARTICIPANT_IN_MATCH",
                    registry.reserve(
                        new RoomMatchDraft(token, 1, session, draft.settings(), List.of(first, second), null, 0)
                    ).reason()
                );
                assertTrue(registry.current(token).isEmpty());
            }

            var token = new RoomMatchToken(new RoomId(2), UUID.randomUUID(), UUID.randomUUID());
            var session = UUID.randomUUID();
            var separate = participant(a, token, session, other);
            var separateOther = participant(
                b,
                token,
                UUID.randomUUID(),
                CharacterFixtures.character(1000, new AccountId(1000), character.gender(), "Other")
            );

            assertEquals(
                RESERVED,
                registry.reserve(
                    new RoomMatchDraft(token, 1, session, draft.settings(), List.of(separate, separateOther), null, 0)
                ).outcome()
            );
            assertTrue(registry.current(draft.token()).isPresent());
            assertTrue(registry.current(token).isPresent());
        }
    }

    private RoomMatchParticipant participant(
        RoomMatchParticipant original,
        RoomMatchToken token,
        UUID session,
        GameCharacter character
    ) {
        return new RoomMatchParticipant(
            new RoomMembership(session, token.roomId(), token.generation(), 1, UUID.randomUUID()),
            new RoomSlot(original.slot().index(), original.slot().state(), new RoomParticipant(session, character, 0))
        );
    }
}
