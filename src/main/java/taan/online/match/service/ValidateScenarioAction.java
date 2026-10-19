package taan.online.match.service;

import static taan.online.logging.LogField.admissionId;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.commandCount;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomGeneration;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.rootBytes;
import static taan.online.logging.LogField.scenarioHash;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.turnRevision;

import java.util.Optional;
import taan.online.logging.EventLogger;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.MatchParticipant;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.AcceptedScenario;
import taan.online.match.service.model.ScenarioAdmissionContext;
import taan.online.match.service.model.ScenarioAdmissionOutcome;
import taan.online.match.service.model.ScenarioAdmissionResult;
import taan.online.match.service.model.ScenarioClaims;

public final class ValidateScenarioAction {
    private static final EventLogger LOG = EventLogger.forClass(ValidateScenarioAction.class);
    private final MatchAdmissionAccess access;
    private final ScenarioRules rules = new ScenarioRules();

    public ValidateScenarioAction(MatchAdmissionAccess access) {
        this.access = access;
    }

    public ScenarioAdmissionResult execute(ScenarioAdmissionContext context, ScenarioClaims claims, byte[] root) {
        try {
            return evaluate(context, claims, root);
        } catch (RuntimeException failure) {
            LOG.failure(
                MatchLogEvent.OPERATION_FAILED,
                failure,
                matchId(context.sender().matchId().value()),
                sessionId(context.sender().sessionId())
            );
            throw failure;
        }
    }

    ScenarioAdmissionResult evaluate(ScenarioAdmissionContext context, ScenarioClaims claims, byte[] root) {
        long started = System.nanoTime();
        var match = context.match();
        var sender = context.sender();
        var member = match.participants().stream().filter(
            participant -> participant.sessionId().equals(sender.sessionId())
                && participant.admissionId().equals(sender.admissionId())
        ).findFirst();
        ScenarioAdmissionResult result;

        if (!matches(context) || member.isEmpty() || !access.current(sender, member.orElseThrow())) {
            result = result(ScenarioAdmissionOutcome.IGNORED, "STALE_MEMBERSHIP");
        } else if (context.previous().isPresent()) {
            var previous = context.previous().orElseThrow();
            boolean duplicate = match.phase() == MatchPhase.COMPLETION && previous.sender().equals(sender)
                && previous.turnRevision() == context.expectedTurnRevision()
                && previous.hash().equals(AcceptedScenario.hash(root));
            result = result(
                duplicate ? ScenarioAdmissionOutcome.DUPLICATE : ScenarioAdmissionOutcome.REJECTED,
                duplicate ? "EXACT_DUPLICATE" : "SCENARIO_ALREADY_ACCEPTED"
            );
        } else if (match.phase() != MatchPhase.ACTION) {
            result = result(ScenarioAdmissionOutcome.IGNORED, "INACTIVE_PHASE");
        } else {
            result = validate(context, claims, root, member.orElseThrow());
        }

        var event = switch (result.outcome()) {
            case ACCEPTED -> MatchLogEvent.SCENARIO_VALIDATED;
            case REJECTED -> MatchLogEvent.SCENARIO_REJECTED;
            case DUPLICATE -> MatchLogEvent.SCENARIO_DUPLICATE;
            case IGNORED -> MatchLogEvent.SCENARIO_IGNORED;
        };
        LOG.log(
            event,
            matchId(sender.matchId().value()),
            roomId(sender.roomId().value()),
            roomGeneration(sender.roomGeneration()),
            channelId(sender.channelId()),
            sessionId(sender.sessionId()),
            admissionId(sender.admissionId()),
            turnRevision(context.expectedTurnRevision()),
            scenarioHash(AcceptedScenario.hash(root)),
            rootBytes(root.length),
            commandCount(claims.commands().size()),
            reason(result.reason()),
            durationMs((System.nanoTime() - started) / 1000000)
        );
        return result;
    }

    private boolean matches(ScenarioAdmissionContext context) {
        var match = context.match();
        var sender = context.sender();
        return match.id().equals(sender.matchId()) && match.roomId().equals(sender.roomId())
            && match.roomGeneration().equals(sender.roomGeneration()) && match.channelId() == sender.channelId()
            && match.turn().revision() == context.expectedTurnRevision();
    }

    private ScenarioAdmissionResult validate(
        ScenarioAdmissionContext context,
        ScenarioClaims claims,
        byte[] root,
        MatchParticipant member
    ) {
        try {
            if (root.length > 65536) {
                throw new ScenarioRejectedException("ROOT_TOO_LARGE");
            }

            var journal = rules.validate(context.match(), claims, member);
            var accepted = new AcceptedScenario(context.sender(), context.expectedTurnRevision(), root, journal);
            return new ScenarioAdmissionResult(ScenarioAdmissionOutcome.ACCEPTED, "VALIDATED", Optional.of(accepted));
        } catch (ScenarioRejectedException failure) {
            return result(ScenarioAdmissionOutcome.REJECTED, failure.getMessage());
        }
    }

    private ScenarioAdmissionResult result(ScenarioAdmissionOutcome outcome, String reason) {
        return new ScenarioAdmissionResult(outcome, reason, Optional.empty());
    }
}
