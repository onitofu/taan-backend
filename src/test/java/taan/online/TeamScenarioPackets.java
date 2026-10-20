package taan.online;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import taan.online.match.model.MatchItemSelectionRules;
import taan.online.match.protocol.packet.GameScenario;
import taan.online.match.protocol.packet.PcGamePlayScenario;
import taan.online.match.protocol.packet.ScenarioCommand;
import taan.online.match.protocol.packet.ScenarioCommandHeader;
import taan.online.match.protocol.packet.ScenarioDamageCommand;
import taan.online.match.protocol.packet.ScenarioFireCommand;
import taan.online.match.protocol.packet.ScenarioGuidedCommand;
import taan.online.match.protocol.packet.ScenarioNormalCommand;
import taan.online.match.protocol.packet.ScenarioWeaponCommand;
import taan.online.match.protocol.packet.ScenarioWeaponFields;
import taan.online.match.service.model.MatchSnapshot;

public final class TeamScenarioPackets {
    private TeamScenarioPackets() {
    }

    public static PcGamePlayScenario shot(MatchSnapshot snapshot, List<Integer> selected, int target, int damage)
        throws Exception {
        int slot = snapshot.turn().activeSlot();
        var member = snapshot.participants().stream().filter(p -> p.slotIndex() == slot).findFirst().orElseThrow();
        int kind = MatchItemSelectionRules.attackKind(selected);
        int launches = 1 + selected.stream().filter(id -> id == 1 || id == 2).mapToInt(Integer::intValue).sum();
        int facing = snapshot.actors().get(member.sessionId()).facing();
        boolean intrinsic = member.weaponId() == 3 && kind == 2;
        var source = intrinsic || selected.contains(7)
            ? MatchGuidedTestPackets.shot(slot, facing, selected, kind, launches)
            : selected.contains(3) ? MatchSpreadTestPackets.shot(slot, facing, selected, kind, launches, .5f)
                : MatchRepeatTestPackets.shot(slot, facing, selected, kind, launches);
        return weapon(snapshot, source, target, damage);
    }

    public static PcGamePlayScenario weapon(MatchSnapshot snapshot, PcGamePlayScenario source, int target, int damage) {
        int slot = source.shooterSlot();
        var member = snapshot.participants().stream().filter(p -> p.slotIndex() == slot).findFirst().orElseThrow();
        int kind = MatchItemSelectionRules.attackKind(source.scenario().selectedItems());
        int animation = (member.weaponId() == 0 ? 9 : member.weaponId() == 8 ? 6 : 12) + kind;
        int delay = kind == 2 ? 2500 : 0;
        int variant = member.weaponId() == 3 && kind == 2 ? 4 : kind;
        var commands = new ArrayList<ScenarioCommand>();

        if (kind == 2) {
            commands.add(new ScenarioNormalCommand(
                new ScenarioCommandHeader(slot, 14, 0, 0),
                member.weaponId() == 0 ? 39 : member.weaponId() == 8 ? 38 : 40,
                0x3f800000
            ));
        }

        commands.add(new ScenarioNormalCommand(new ScenarioCommandHeader(slot, 9, delay, 0), animation, 103));

        for (var event : source.scenario().commands()) {
            var h = event.header();
            var header = new ScenarioCommandHeader(h.slot(), h.kind(), h.tick() + delay, h.scalar10());

            if (event instanceof ScenarioFireCommand fire) {
                var w = fire.weapon();
                var fields = new ScenarioWeaponFields(
                    w.sequence(),
                    variant,
                    w.position(),
                    w.shotTick() + delay,
                    w.scalar24Bits(),
                    w.scalar28()
                );
                commands.add(new ScenarioFireCommand(
                    header,
                    fields,
                    member.weaponId(),
                    kind,
                    fire.velocity(),
                    fire.acceleration(),
                    fire.byte40(),
                    fire.word42()
                ));
            } else if (event instanceof ScenarioDamageCommand hit) {
                commands.add(new ScenarioDamageCommand(
                    new ScenarioCommandHeader(target, 4, header.tick(), 0),
                    damage,
                    hit.simulationHp(),
                    0,
                    0
                ));
            } else if (event instanceof ScenarioWeaponCommand value) {
                var w = value.weapon();
                commands.add(new ScenarioWeaponCommand(
                    header,
                    new ScenarioWeaponFields(
                        w.sequence(),
                        variant,
                        w.position(),
                        w.shotTick() + delay,
                        w.scalar24Bits(),
                        w.scalar28()
                    )
                ));
            } else if (event instanceof ScenarioGuidedCommand value) {
                var w = value.weapon();
                commands.add(new ScenarioGuidedCommand(
                    header,
                    new ScenarioWeaponFields(
                        w.sequence(),
                        variant,
                        w.position(),
                        w.shotTick() + delay,
                        w.scalar24Bits(),
                        w.scalar28()
                    ),
                    value.target()
                ));
            } else {
                commands.add(MatchScenarioTestPackets.withHeader(event, header));
            }
        }

        commands.sort(Comparator.comparingInt(c -> c.header().tick()));
        var s = source.scenario();
        return new PcGamePlayScenario(
            source.facing(),
            slot,
            new GameScenario(
                s.slot(),
                s.scalar09(),
                commands,
                s.selectedItems(),
                s.effects(),
                s.flags(),
                s.power(),
                s.refittedSlots(),
                s.scalar0c()
            ),
            source.scalar18(),
            source.scalar1c(),
            source.tail()
        );
    }
}
