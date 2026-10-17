package taan.online.character.controller;

import taan.online.character.protocol.packet.PrSelectCharacter;

public record SelectCharacterHandlingResult(SelectCharacterHandlingOutcome outcome, PrSelectCharacter response) {
}
