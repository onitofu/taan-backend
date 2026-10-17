package taan.online.character.controller;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import taan.online.character.protocol.codec.SelectCharacterRequestCodec;
import taan.online.character.protocol.packet.PrSelectCharacter;
import taan.online.character.service.CharacterSelectionAccess;
import taan.online.character.service.SelectCharacterAction;
import taan.online.character.service.model.SelectCharacterOutcome;
import taan.online.inventory.controller.InventoryMapper;
import taan.online.inventory.model.InventoryKind;

public final class SelectCharacterPacketHandler {
    private static final List<InventoryKind> ORDER = List.of(
        InventoryKind.BAG,
        InventoryKind.EQUIPMENT,
        InventoryKind.JEWEL_BOX
    );

    private final SelectCharacterAction action;
    private final CharacterSelectionAccess sessions;

    public SelectCharacterPacketHandler(SelectCharacterAction action, CharacterSelectionAccess sessions) {
        this.action = action;
        this.sessions = sessions;
    }

    public SelectCharacterHandlingResult handle(UUID sessionId, byte[] root) {
        if (sessions.authenticatedAccount(sessionId).isEmpty()) {
            return new SelectCharacterHandlingResult(SelectCharacterHandlingOutcome.IGNORED, null);
        }

        var request = new SelectCharacterRequestCodec().decode(root);
        var result = action.execute(sessionId, request.characterId());

        if (result.outcome() == SelectCharacterOutcome.IGNORED || sessions.selectedCharacter(sessionId).isEmpty()
            && result.outcome() == SelectCharacterOutcome.SELECTED) {
            return new SelectCharacterHandlingResult(SelectCharacterHandlingOutcome.IGNORED, null);
        }

        if (result.outcome() == SelectCharacterOutcome.REJECTED) {
            return new SelectCharacterHandlingResult(SelectCharacterHandlingOutcome.REJECTED, null);
        }

        var info = new CharacterCatalogMapper().map(List.of(result.character())).info().getFirst();
        var inventories = result.inventories().stream()
            .sorted(Comparator.comparingInt(value -> ORDER.indexOf(value.kind())))
            .map(new InventoryMapper()::map).toList();
        return new SelectCharacterHandlingResult(
            SelectCharacterHandlingOutcome.SELECTED,
            new PrSelectCharacter(info, inventories)
        );
    }
}
