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
        if (weaponId != 0 || attackKind != 0 || minPower < 0 || maxPower > 100 || minPower > maxPower
            || missileVariant != 0 || missileTypeId != 0) {
            throw new IllegalArgumentException("Unsupported ordinary cannon definition");
        }
    }
}
