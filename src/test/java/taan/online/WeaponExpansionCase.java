package taan.online;

import java.util.List;

public record WeaponExpansionCase(
    String name,
    String filename,
    String sha256,
    int weaponId,
    int attackKind,
    int shooter,
    int facing,
    List<Integer> selectedItems,
    String fireBindings,
    String animations,
    String commandKinds,
    int damage,
    boolean captain
) {
    @Override
    public String toString() {
        return name;
    }
}
