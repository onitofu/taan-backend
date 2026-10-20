package taan.online.match.model;

public record CannonShotDefinition(
    int weaponId,
    int attackKind,
    int minPower,
    int maxPower,
    int missileVariant,
    int missileTypeId
) {
    public CannonShotDefinition {
        if (weaponId != 0 || attackKind < 0 || attackKind > 2 || minPower < 0 || maxPower > 100 || minPower > maxPower
            || missileVariant != attackKind || missileTypeId != attackKind) {
            throw new IllegalArgumentException("Unsupported cannon definition");
        }
    }
}
