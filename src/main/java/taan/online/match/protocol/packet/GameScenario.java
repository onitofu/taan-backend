package taan.online.match.protocol.packet;

import java.util.List;

public record GameScenario(
    int slot,
    int scalar09,
    List<ScenarioCommand> commands,
    List<Integer> selectedItems,
    List<ScenarioWeaponEffect> effects,
    int flags,
    float power,
    List<Integer> refittedSlots,
    int scalar0c
) {
    public static final int TYPE_ID = 0x1d3a04ae;

    public GameScenario {
        commands = List.copyOf(commands);
        selectedItems = List.copyOf(selectedItems);
        effects = List.copyOf(effects);
        refittedSlots = List.copyOf(refittedSlots);
    }
}
