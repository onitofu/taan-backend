package taan.online.match.model;

import java.util.List;

public record MatchLifecycleTransition(
    MatchPhase before,
    MatchPhase after,
    long revision,
    MatchTransitionDecision decision,
    List<MatchLifecycleEffect> effects
) {
    public MatchLifecycleTransition {
        effects = List.copyOf(effects);
    }
}
