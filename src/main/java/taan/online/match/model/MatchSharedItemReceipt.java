package taan.online.match.model;

import java.util.UUID;

public record MatchSharedItemReceipt(UUID inventoryOwner, MatchItemRoute route, MatchItemReceipt receipt) {
}
