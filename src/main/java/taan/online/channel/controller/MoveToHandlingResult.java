package taan.online.channel.controller;

import taan.online.channel.protocol.packet.PrChannelMoveTo;

public record MoveToHandlingResult(ChannelHandlingOutcome outcome, PrChannelMoveTo response) {
}
