package taan.online.match.service;

import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.ScenarioCommitReceipt;
import taan.online.match.model.ScenarioWorldDraft;
import taan.online.match.model.ScenarioWorldException;
import taan.online.match.service.model.AcceptedScenario;
import taan.online.match.service.model.MatchSnapshot;
import taan.online.match.service.model.PreparedScenario;
import taan.online.match.service.model.ScenarioActorClaim;
import taan.online.match.service.model.ScenarioDamageClaim;
import taan.online.match.service.model.ScenarioFallClaim;
import taan.online.match.service.model.ScenarioFireClaim;
import taan.online.match.service.model.ScenarioProjectileClaim;
import taan.online.match.service.model.ScenarioUnsupportedClaim;

public final class PrepareScenarioAction {
    public PreparedScenario execute(MatchSnapshot snapshot, AcceptedScenario scenario) {
        var sessions = snapshot.participants().stream().collect(
            Collectors.toUnmodifiableMap(MatchParticipant::slotIndex, MatchParticipant::sessionId)
        );
        var journal = scenario.journal();
        var draft = new ScenarioWorldDraft(snapshot.world(), snapshot.definitions(), snapshot.participants());

        for (var event : journal.events()) {
            var header = event.header();
            var session = sessions.get(header.slot());

            if (session == null) {
                throw new ScenarioWorldException("UNKNOWN_ACTOR");
            }

            switch (event) {
                case ScenarioDamageClaim damage -> draft.damage(session, damage.damage());
                case ScenarioFallClaim fall -> draft.fall(
                    session,
                    header.tick(),
                    header.duration(),
                    fall.startY(),
                    fall.endY()
                );
                case ScenarioProjectileClaim projectile -> {
                    if (header.kind() == 2) {
                        var missile = journal.missileDefinitions().get(projectile.sequence());

                        if (missile == null) {
                            throw new ScenarioWorldException("UNKNOWN_PROJECTILE_TERRAIN");
                        }

                        draft.explode(missile, projectile.x(), projectile.y(), projectile.terrainFactor());
                    }
                }
                case ScenarioActorClaim ignored -> {
                    if (header.kind() == 5 || header.kind() == 7) {
                        draft.dead(session);
                    }
                }
                case ScenarioFireClaim ignored -> { }
                case ScenarioUnsupportedClaim ignored -> throw new ScenarioWorldException("UNSUPPORTED_FEATURE");
            }
        }

        var refitted = new HashSet<UUID>();

        for (int slot : journal.refittedSlots()) {
            var session = sessions.get(slot);

            if (session == null) {
                throw new ScenarioWorldException("UNKNOWN_REFITTED_ACTOR");
            }

            refitted.add(session);
        }

        var sender = scenario.sender();
        var receipt = new ScenarioCommitReceipt(
            scenario.hash(),
            sender.sessionId(),
            sender.admissionId(),
            scenario.turnRevision(),
            Math.incrementExact(snapshot.world().revision())
        );
        var world = draft.finish(receipt, refitted, journal.endTick());
        return new PreparedScenario(
            scenario,
            snapshot.world().revision(),
            world,
            draft.changedCells(),
            world.processedDeaths().size() - snapshot.world().processedDeaths().size()
        );
    }
}
