package taan.online.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import taan.online.CharacterFixtures;
import taan.online.MatchRoomFixture;
import taan.online.account.model.AccountId;
import taan.online.character.model.GameCharacter;
import taan.online.match.model.MatchParticipant;
import taan.online.match.service.model.MatchCommandContext;
import taan.online.room.model.RoomMode;

class ChannelMatchAccessTest {
    @Test
    void requiresTheCurrentChannelAndBothAuthenticatedPersistentIdentities() {
        try (var f = new MatchRoomFixture(2, RoomMode.ITEM)) {
            var token = f.scenario.rooms.reserveMatch(f.owner).token();
            var context = f.scenario.matches.context(f.owner);
            var participant = f.scenario.matches.current(token).orElseThrow().participants().getFirst();
            var access = new ChannelMatchAccess(f.scenario.channels);

            assertTrue(access.current(context, participant));
            assertFalse(
                access.current(
                    new MatchCommandContext(
                        context.matchId(),
                        context.roomId(),
                        context.roomGeneration(),
                        2,
                        context.sessionId(),
                        context.admissionId()
                    ),
                    participant
                )
            );
            assertFalse(
                access.current(
                    context,
                    withCharacter(
                        participant,
                        CharacterFixtures.character(1, new AccountId(99), participant.character().gender(), "Other")
                    )
                )
            );
            assertFalse(
                access.current(
                    context,
                    withCharacter(
                        participant,
                        CharacterFixtures.character(
                            99,
                            participant.character().accountId(),
                            participant.character().gender(),
                            "Other"
                        )
                    )
                )
            );
            f.scenario.lobby(f.owner);

            assertFalse(access.current(context, participant));
        }
    }

    private MatchParticipant withCharacter(MatchParticipant source, GameCharacter character) {
        return new MatchParticipant(
            source.sessionId(),
            source.admissionId(),
            source.slotIndex(),
            source.team(),
            character,
            source.weaponId(),
            source.spawn(),
            source.facing(),
            source.hp(),
            source.maxHp(),
            source.energy(),
            source.maxEnergy(),
            source.roomSlotState()
        );
    }
}
