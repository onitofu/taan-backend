package taan.online.inventory.controller;

import java.util.List;

public record InventoryHandlingResult(boolean ignored, List<byte[]> responses) {
    public InventoryHandlingResult {
        responses = List.copyOf(responses);
    }
}
