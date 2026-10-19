package taan.online.match.service;

import static taan.online.logging.LogField.admissionId;
import static taan.online.logging.LogField.changedCells;
import static taan.online.logging.LogField.channelId;
import static taan.online.logging.LogField.durationMs;
import static taan.online.logging.LogField.matchId;
import static taan.online.logging.LogField.processedDeaths;
import static taan.online.logging.LogField.reason;
import static taan.online.logging.LogField.roomGeneration;
import static taan.online.logging.LogField.roomId;
import static taan.online.logging.LogField.scenarioHash;
import static taan.online.logging.LogField.sessionId;
import static taan.online.logging.LogField.turnRevision;
import static taan.online.logging.LogField.worldRevision;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import taan.online.match.service.model.PendingMatchSpend;
import taan.online.logging.EventLogger;
import taan.online.logging.LogContext;
import taan.online.match.logging.MatchLogEvent;
import taan.online.match.model.ScenarioWorldException;
import taan.online.match.model.MatchPhase;
import taan.online.match.service.model.ApplyScenarioCommand;
import taan.online.match.service.model.CommittedScenario;
import taan.online.match.service.model.ScenarioAdmissionContext;
import taan.online.match.service.model.ScenarioApplicationOutcome;
import taan.online.match.service.model.ScenarioApplicationResult;
import taan.online.room.service.model.RoomMatchEndReason;

final class MatchScenarioExecution {
    private static final EventLogger LOG = EventLogger.forClass(MatchScenarioExecution.class);
    private final MatchRuntime owner;
    private final ValidateScenarioAction validator;
    private final PrepareScenarioAction prepare = new PrepareScenarioAction();
    private final ScenarioRelay relay;
    private volatile CommittedScenario previous;

    MatchScenarioExecution(MatchRuntime owner, MatchAdmissionAccess access) {
        this.owner = owner;
        validator = new ValidateScenarioAction(access);
        relay = new ScenarioRelay(access, owner::running);
    }

    void reset() {
        previous = null;
    }

    ScenarioRelay relay() {
        return relay;
    }

    ScenarioApplicationResult execute(ApplyScenarioCommand command) {
        try {
            return apply(command);
        } catch (ScenarioWorldException failure) {
            LOG.log(
                MatchLogEvent.SCENARIO_REJECTED,
                reason(failure.getMessage()),
                matchId(command.context().matchId().value())
            );
            owner.terminate(RoomMatchEndReason.INVALID_SCENARIO);
            return new ScenarioApplicationResult(
                ScenarioApplicationOutcome.REJECTED,
                failure.getMessage(),
                Optional.empty()
            );
        } catch (RuntimeException failure) {
            LOG.failure(MatchLogEvent.SCENARIO_APPLY_FAILED, failure, matchId(command.context().matchId().value()));
            owner.terminate(RoomMatchEndReason.PREPARATION_FAILED);
            return new ScenarioApplicationResult(ScenarioApplicationOutcome.REJECTED, "APPLY_FAILED", Optional.empty());
        }
    }

    private ScenarioApplicationResult apply(ApplyScenarioCommand command) {
        long started = System.nanoTime();
        var before = owner.snapshot();
        long revision = before.world().revision();

        boolean sameAction = before.phase() == MatchPhase.ACTION && command.phaseRevision() == before.phaseRevision();

        if (!owner.scenarioCurrent(command) || command.worldRevision() != revision && !sameAction
            && (previous == null || command.worldRevision() != previous.snapshot().world().revision() - 1)) {
            LOG.log(
                MatchLogEvent.SCENARIO_IGNORED,
                reason("STALE_WORLD"),
                matchId(command.context().matchId().value())
            );
            return ScenarioApplicationResult.ignored("STALE_WORLD");
        }

        var context = new ScenarioAdmissionContext(
            before,
            command.context(),
            command.turnRevision(),
            Optional.ofNullable(previous).map(CommittedScenario::scenario)
        );
        var admission = validator.evaluate(context, command.claims(), command.root());

        var refusal = switch (admission.outcome()) {
            case DUPLICATE -> new ScenarioApplicationResult(
                ScenarioApplicationOutcome.DUPLICATE,
                admission.reason(),
                Optional.of(previous)
            );
            case IGNORED -> ScenarioApplicationResult.ignored(admission.reason());
            case REJECTED -> {
                owner.terminate(RoomMatchEndReason.INVALID_SCENARIO);
                yield new ScenarioApplicationResult(
                    ScenarioApplicationOutcome.REJECTED,
                    admission.reason(),
                    Optional.empty()
                );
            }
            case ACCEPTED -> null;
        };

        if (refusal != null) {
            return refusal;
        }

        var spend = owner.items.prepare();
        var prepared = prepare.execute(before, admission.accepted().orElseThrow());
        var publication = before.completed(prepared.world(), owner.completion(prepared));
        var committed = new CommittedScenario(
            prepared.scenario(),
            publication,
            prepared.changedCells(),
            prepared.deaths()
        );

        var result = new ScenarioApplicationResult(
            ScenarioApplicationOutcome.APPLIED,
            "APPLIED",
            Optional.of(committed)
        );

        if (!owner.commitScenario(command, prepared, publication)) {
            LOG.log(
                MatchLogEvent.SCENARIO_IGNORED,
                reason("STALE_COMMIT"),
                matchId(command.context().matchId().value())
            );
            return ScenarioApplicationResult.ignored("STALE_COMMIT");
        }

        previous = committed;
        MatchEnergyLog.scenario(before, committed);
        var sender = command.context();
        LOG.log(
            MatchLogEvent.SCENARIO_APPLIED,
            matchId(sender.matchId().value()),
            roomId(sender.roomId().value()),
            roomGeneration(sender.roomGeneration()),
            channelId(sender.channelId()),
            sessionId(sender.sessionId()),
            admissionId(sender.admissionId()),
            turnRevision(command.turnRevision()),
            worldRevision(publication.world().revision()),
            scenarioHash(committed.scenario().hash()),
            changedCells(committed.changedCells()),
            processedDeaths(committed.deaths()),
            durationMs((System.nanoTime() - started) / 1000000)
        );
        publish(committed, result, spend);
        return result;
    }

    private void publish(
        CommittedScenario committed,
        ScenarioApplicationResult result,
        Optional<PendingMatchSpend> spend
    ) {
        try {
            var spending = owner.items.committed(spend);
            var scenario = owner.publishScenario(committed, result);
            CompletableFuture.allOf(spending.toCompletableFuture(), scenario.toCompletableFuture()).handle(
                LogContext.propagate(
                    (ignored, failure) -> {
                        if (failure != null) {
                            owner.scenarioDeliveryFailed(committed, failure);
                        }

                        return null;
                    }
                )
            );
        } catch (RuntimeException failure) {
            deliveryFailed(committed, failure);
        }
    }

    boolean deliveryFailed(CommittedScenario committed, Throwable failure) {
        if (previous != committed || !owner.admits(committed.scenario().sender())) {
            return false;
        }

        LOG.failure(
            MatchLogEvent.SCENARIO_DELIVERY_FAILED,
            failure,
            matchId(committed.scenario().sender().matchId().value()),
            scenarioHash(committed.scenario().hash())
        );
        owner.terminate(RoomMatchEndReason.SCENARIO_DELIVERY_FAILED);
        return true;
    }
}
