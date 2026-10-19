package taan.online.match.service.model;

import java.util.List;
import java.util.Map;

public record ScenarioJournal(
    List<ScenarioClaim> events,
    Map<Integer, Integer> missileDefinitions,
    List<Integer> refittedSlots,
    int headerEnergyGrant,
    long endTick
) {
    public ScenarioJournal {
        events = List.copyOf(events);
        missileDefinitions = Map.copyOf(missileDefinitions);
        refittedSlots = List.copyOf(refittedSlots);
    }
}
