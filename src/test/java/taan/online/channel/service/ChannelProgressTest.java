package taan.online.channel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import taan.online.SelectionFixtures;
import taan.online.channel.service.model.MoveToCommand;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterProgress;
import taan.online.character.model.CharacterProgressSnapshot;

class ChannelProgressTest {
    @Test
    void publishesNewLevelsOnlyInsideTheCurrentChannelAndHandlesConcurrentSelectionLoss() {
        var access = new ChannelTestAccess();
        var first = access.add(1, 1);
        var second = access.add(2, 1);
        var foreign = access.add(3, 1);
        var registry = new ChannelRegistry(access, 2181);
        registry.initialize(SelectionFixtures.directory().groups());
        registry.move(first, new MoveToCommand(0, 1, 0, 929));
        registry.move(second, new MoveToCommand(0, 1, 0, 929));
        registry.move(foreign, new MoveToCommand(0, 2, 0, 929));
        var local = new RecordingChannelOutput();
        var remote = new RecordingChannelOutput();
        registry.enter(first, new RecordingChannelOutput());
        registry.enter(second, local);
        registry.enter(foreign, remote);
        local.added.clear();
        remote.added.clear();
        var progress = new CharacterProgressSnapshot(new CharacterId(1), 2, new CharacterProgress(940, 0, 0, 0));

        assertFalse(registry.refreshProgress(first, progress));
        access.allowRefresh = true;
        assertTrue(registry.refreshProgress(first, progress));
        assertEquals(2, local.added.getFirst().level());
        assertTrue(remote.added.isEmpty());
        access.onRefresh = () -> access.players.remove(first);
        assertFalse(registry.refreshProgress(first, progress));
        access.players.put(first, ChannelTestAccess.participant(first, 1, 2));
        access.onRefresh = () -> access.players.put(first, ChannelTestAccess.participant(first, 99, 2));
        assertFalse(registry.refreshProgress(first, progress));
    }
}
