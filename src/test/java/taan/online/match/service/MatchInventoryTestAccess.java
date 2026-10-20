package taan.online.match.service;

import taan.online.match.model.Match;
import taan.online.match.model.MatchBonusOffer;
import taan.online.room.service.model.RoomMatchToken;

public final class MatchInventoryTestAccess {
    private MatchInventoryTestAccess() {
    }

    public static void grantControlled(MatchRegistry registry, RoomMatchToken token, int slot, int item)
        throws ReflectiveOperationException {
        var find = MatchRegistry.class.getDeclaredMethod("find", RoomMatchToken.class);
        find.setAccessible(true);
        var runtime = (MatchRuntime) find.invoke(registry, token);
        var field = MatchRuntime.class.getDeclaredField("match");
        field.setAccessible(true);
        var match = (Match) field.get(runtime);
        runtime.turnWork(() -> {
            var member = match.participants().stream().filter(value -> value.slotIndex() == slot)
                .findFirst().orElseThrow();
            var loot = match.loot().offered(new MatchBonusOffer(
                member.sessionId(),
                member.admissionId(),
                match.turn().revision(),
                0,
                false
            )).granted(member.sessionId(), item);
            match.updateLoot(loot);
            runtime.refresh();
            return true;
        }).join();
    }
}
