package taan.online.match.service.model;

import java.util.List;

public record ScenarioClaims(
    int shooterSlot,
    int scenarioSlot,
    int facing,
    int flags,
    float power,
    int headerEnergyGrant,
    List<Integer> selectedItems,
    boolean effectsPresent,
    List<Integer> refittedSlots,
    List<ScenarioClaim> commands
) {
    public ScenarioClaims {
        selectedItems = List.copyOf(selectedItems);
        refittedSlots = List.copyOf(refittedSlots);
        commands = List.copyOf(commands);
    }
}
