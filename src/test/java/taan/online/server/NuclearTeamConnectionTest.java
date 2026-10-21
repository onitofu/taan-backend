package taan.online.server;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static taan.online.server.RoomConnectionWorld.read;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import taan.online.LogCapture;
import taan.online.TeamScenarioPackets;
import taan.online.TestFixtures;
import taan.online.channel.model.ChannelDefinition;
import taan.online.channel.model.ChannelGroupDefinition;
import taan.online.match.model.MatchBoxPolicy;
import taan.online.match.model.MatchItemSelectionRules;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.protocol.codec.EatBonusItemCodec;
import taan.online.match.protocol.codec.NotifyEatItemCodec;
import taan.online.match.protocol.codec.PassTurnCodec;
import taan.online.match.protocol.codec.ScenarioCodec;
import taan.online.match.protocol.codec.SpendItemCodec;
import taan.online.match.protocol.codec.UseItemCodec;
import taan.online.match.protocol.packet.PcGamePlayEatBonusItem;
import taan.online.match.protocol.packet.PcGamePlayEndScenario;
import taan.online.match.protocol.packet.PcGamePlayNotifyEatItem;
import taan.online.match.protocol.packet.PcGamePlayPassTurn;
import taan.online.match.protocol.packet.PcGamePlaySpendItem;
import taan.online.match.protocol.packet.PcGamePlayUseItem;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.service.model.MatchExecutionMode;
import taan.online.match.service.model.MatchLootPolicy;
import taan.online.protocol.BinaryWriter;

class NuclearTeamConnectionTest {
    @Test
    void preservesActivationAcrossPassAndTimeoutAndReturnsEveryRoleToItsBaseWeaponAfterShooting() throws Exception {
        for (int weapon : new int[]{0, 3, 8, 10}) {
            for (boolean captain : new boolean[]{false, true}) {
                try (var f = fixture(weapon, captain); var logs = new LogCapture()) {
                    for (var member : f.snapshot().participants()) {
                        until(f, member.sessionId());
                        pass(f);
                        byte[] bonus = new EatBonusItemCodec().encode(new PcGamePlayEatBonusItem(
                            member.slotIndex(),
                            new ScenarioEatItemBoxCommand(
                                new ScenarioCommandHeader(member.slotIndex(), 11, -17, -3),
                                -1L,
                                4,
                                -1
                            ),
                            Optional.empty()
                        ));
                        f.world.request(f.sessions.get(member.sessionId()), bonus);
                        broadcast(f, bonus);
                        f.world.request(f.sessions.get(member.sessionId()), new NotifyEatItemCodec().encode(
                            new PcGamePlayNotifyEatItem(member.slotIndex(), 0, 4)
                        ));
                        until(f, member.sessionId());
                        var before = f.snapshot().actors().get(member.sessionId());
                        byte[] use = new UseItemCodec().encode(new PcGamePlayUseItem(
                            member.slotIndex(),
                            (member.slotIndex() & 2) == 0 ? 1 : 2,
                            0,
                            0,
                            0,
                            4
                        ));
                        f.world.request(f.sessions.get(member.sessionId()), use);
                        broadcast(f, use);
                        f.world.request(f.sessions.get(member.sessionId()), use);
                        assertEquals(before, f.snapshot().actors().get(member.sessionId()));
                        assertEquals(List.of(4), f.snapshot().attacks().get(member.sessionId()).activeItems());

                        pass(f);
                        until(f, member.sessionId());
                        f.world.matchTimers.advanceSeconds(20);
                        f.world.matchTimers.advanceSeconds(1);
                        f.world.tick();
                        f.world.drainOutputs();
                        until(f, member.sessionId());
                        assertEquals(List.of(4), f.snapshot().attacks().get(member.sessionId()).activeItems());
                        shoot(f, member, true);
                        until(f, member.sessionId());
                        assertFalse(f.snapshot().attacks().containsKey(member.sessionId()));
                        shoot(f, member, false);
                        assertEquals(weapon, member.weaponId());
                    }

                    assertTrue(logs.text().contains("match.nuclear.activated"));
                    assertTrue(logs.text().contains("match.nuclear.duplicate"));
                    assertTrue(logs.text().contains("effectiveWeaponId=\"100\""));
                    assertFalse(logs.text().contains(TestFixtures.CREDENTIAL));
                    assertFalse(logs.text().contains(TestFixtures.VERIFIER));
                }
            }
        }
    }

    private TeamConnectionFixture fixture(int weapon, boolean captain) throws Exception {
        var world = new RoomConnectionWorld(
            MatchExecutionMode.CLIENT_SCENARIO,
            MatchLootPolicy.nativeItems(),
            MatchBoxPolicy.ENABLED,
            List.of(new ChannelGroupDefinition(
                1,
                "Solar",
                0,
                List.of(new ChannelDefinition(1, "Moon", true, 1, 109, 100, 0, 100))
            ))
        );
        return new TeamConnectionFixture(world, 2, 2, captain, weapon);
    }

    private void until(TeamConnectionFixture f, UUID session) {
        for (int attempts = 0; attempts < 4 && !active(f).sessionId().equals(session); attempts++) {
            pass(f);
        }

        assertEquals(session, active(f).sessionId());
    }

    private MatchParticipant active(TeamConnectionFixture f) {
        return f.snapshot().participants().stream().filter(p -> p.slotIndex() == f.snapshot().turn().activeSlot())
            .findFirst().orElseThrow();
    }

    private void pass(TeamConnectionFixture f) {
        var member = active(f);
        var actor = f.snapshot().actors().get(member.sessionId());
        f.world.request(f.sessions.get(member.sessionId()), new PassTurnCodec().encode(new PcGamePlayPassTurn(
            member.slotIndex(),
            actor.x(),
            actor.y(),
            actor.facing(),
            0
        )));
        f.world.drainOutputs();
    }

    private void shoot(TeamConnectionFixture f, MatchParticipant member, boolean nuclear) throws Exception {
        var before = f.snapshot();
        var selected = nuclear ? List.of(4) : List.<Integer>of();

        if (before.captain(member.slotIndex())) {
            selected = nuclear ? List.of(1, 4) : List.of(1);
        }

        byte[] spend = new SpendItemCodec().encode(new PcGamePlaySpendItem(
            member.slotIndex(),
            (member.slotIndex() & 2) == 0 ? 1 : 2,
            255,
            List.of()
        ));
        var client = f.sessions.get(member.sessionId());
        f.world.request(client, spend);
        f.world.request(client, spend);
        byte[] root = new ScenarioCodec().encode(TeamScenarioPackets.shot(
            before,
            selected,
            member.slotIndex() == 0 ? 2 : 0,
            0
        ));
        f.world.request(client, root);
        f.world.request(client, root);
        assertEquals(before.world().revision() + 1, f.snapshot().world().revision());
        assertEquals(MatchPhase.COMPLETION, f.snapshot().phase());
        assertEquals(nuclear ? 100 : member.weaponId(), MatchItemSelectionRules.effectiveWeaponId(
            member.weaponId(),
            selected
        ));
        broadcast(f, spend);

        for (var participant : before.participants()) {
            if (!participant.sessionId().equals(member.sessionId())) {
                assertArrayEquals(root, read(f.sessions.get(participant.sessionId())));
            }
        }

        byte[] ack = new BinaryWriter().unsignedInt(PcGamePlayEndScenario.TYPE_ID)
            .unsignedByte(member.slotIndex()).unsignedByte(0).toByteArray();

        for (var participant : before.participants()) {
            f.world.request(f.sessions.get(participant.sessionId()), ack);
            f.world.request(f.sessions.get(participant.sessionId()), ack);
        }

        assertEquals(MatchPhase.ACTION, f.snapshot().phase());
        f.world.drainOutputs();
        f.clients.forEach(connection -> assertTrue(connection.channel().isActive()));
    }

    private void broadcast(TeamConnectionFixture f, byte[] root) {
        f.clients.forEach(client -> assertArrayEquals(root, read(client)));
    }
}
