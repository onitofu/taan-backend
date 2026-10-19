package taan.online.match.service;

import static taan.online.logging.LogField.energyBefore;
import static taan.online.logging.LogField.energyAfter;
import static taan.online.logging.LogField.headerEnergyGrant;
import static taan.online.logging.LogField.damageEnergyMaximum;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.admissionId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.slotIndex;
import static taan.online.logging.LogField.turnRevision;

import java.util.HashMap;
import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.MatchEnergyRules;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.CommittedScenario;
import taan.online.match.service.model.ScenarioDamageClaim;

final class MatchEnergyLog {
    private static final EventLogger LOG = EventLogger.forClass(MatchEnergyLog.class);

    private MatchEnergyLog() {
    }

    static void scenario(MatchSnapshot before, CommittedScenario committed) {
        var maxima = new HashMap<Integer, Integer>();
        var journal = committed.scenario().journal();

        for (var event : journal.events()) {
            if (event instanceof ScenarioDamageClaim damage) {
                maxima.merge(damage.header().slot(), MatchEnergyRules.maximum(0, damage.energy1e()), Math::max);
                maxima.merge(before.turn().activeSlot(), MatchEnergyRules.maximum(0, damage.energy1c()), Math::max);
            }
        }

        for (var member : before.participants()) {
            LOG.log(
                MatchLogEvent.ENERGY_SCENARIO_APPLIED,
                matchId(before.id().value()),
                slotIndex(member.slotIndex()),
                sessionId(member.sessionId()),
                admissionId(member.admissionId()),
                channelId(before.channelId()),
                turnRevision(before.turn().revision()),
                energyBefore(before.actors().get(member.sessionId()).energy()),
                energyAfter(committed.snapshot().actors().get(member.sessionId()).energy()),
                headerEnergyGrant(member.slotIndex() == before.turn().activeSlot() ? journal.headerEnergyGrant() : 0),
                damageEnergyMaximum(maxima.getOrDefault(member.slotIndex(), 0))
            );
        }
    }

    static void turn(MatchSnapshot before, MatchSnapshot after) {
        var member = after.participants().stream().filter(p -> p.slotIndex() == after.turn().activeSlot())
            .findFirst().orElseThrow();
        LOG.log(
            MatchLogEvent.ENERGY_TURN_GRANTED,
            matchId(after.id().value()),
            slotIndex(member.slotIndex()),
            sessionId(member.sessionId()),
            admissionId(member.admissionId()),
            channelId(after.channelId()),
            turnRevision(after.turn().revision()),
            energyBefore(before.actors().get(member.sessionId()).energy()),
            energyAfter(after.actors().get(member.sessionId()).energy())
        );
    }
}
