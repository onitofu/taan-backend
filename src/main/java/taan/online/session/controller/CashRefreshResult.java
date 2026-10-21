package taan.online.session.controller;

import taan.online.character.protocol.packet.PcUpdateMoney;
import taan.online.session.protocol.packet.PcUpdateCash;

public record CashRefreshResult(PcUpdateCash cash, PcUpdateMoney gold) {
}
