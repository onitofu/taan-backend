package taan.online.character.model;

import java.util.Map;
import java.util.Set;
import taan.online.account.model.AccountGender;

public final class CharacterCreationOptions {
    private static final Set<Integer> CREATION_SLOTS = Set.of(1, 3, 5, 8);

    private final Map<AccountGender, Set<AppearancePart>> allowedParts;

    public CharacterCreationOptions(Map<AccountGender, Set<AppearancePart>> allowedParts) {
        this.allowedParts = Map.of(
            AccountGender.MALE,
            Set.copyOf(allowedParts.get(AccountGender.MALE)),
            AccountGender.FEMALE,
            Set.copyOf(allowedParts.get(AccountGender.FEMALE))
        );

        for (var parts : this.allowedParts.values()) {
            for (var part : parts) {
                if (!CREATION_SLOTS.contains(part.slot()) || part.itemId() == 0) {
                    throw new IllegalArgumentException("Invalid character creation option");
                }
            }

            for (int slot : CREATION_SLOTS) {
                if (parts.stream().noneMatch(part -> part.slot() == slot)) {
                    throw new IllegalArgumentException("Missing character creation category");
                }
            }
        }
    }

    public boolean allows(AccountGender gender, CharacterAppearance appearance) {
        return appearance.parts().stream().allMatch(
            part -> CREATION_SLOTS.contains(part.slot())
                ? allowedParts.get(gender).contains(part)
                : part.equals(new AppearancePart(part.slot(), 0, 0, 0, 0))
        );
    }
}
