package taan.online.room.service.model;

import java.util.concurrent.CompletionStage;

public record SetCaptainCandidateResult(
    SetCaptainCandidateOutcome outcome,
    String reason,
    boolean candidate,
    CompletionStage<Void> delivery
) {
}
