package taan.online.match.protocol.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import taan.online.MatchFixtures;
import taan.online.match.protocol.packet.ScenarioEatItemBoxCommand;
import taan.online.match.protocol.packet.ScenarioUseBonusCommand;

class ScenarioGoldenLayoutsTest {
    @Test
    void coversEveryLayoutWithIndependentNativeOrExplicitSyntheticBytes() throws Exception {
        var codec = new ScenarioCodec();
        var types = new HashSet<String>();

        for (String name : List.of(
            "native-cannon-0-0",
            "native-cannon-2-1",
            "native-cannon-2-0",
            "native-cannon-guided",
            "native-cannon-power",
            "native-cannon-team-gauge",
            "equipment-perks-kind15-fixtures-kind15-slot2-damage10.synthetic",
            "weapon-effects-fix-scenario-fish-native"
        )) {
            byte[] root = MatchFixtures.root(name);
            var packet = codec.decode(root);
            packet.scenario().commands().forEach(command -> types.add(command.getClass().getSimpleName()));

            assertArrayEquals(root, codec.encode(packet));
        }

        assertEquals(
            Set.of(
                "ScenarioBaseCommand",
                "ScenarioWeaponCommand",
                "ScenarioFireCommand",
                "ScenarioGuidedCommand",
                "ScenarioNormalCommand",
                "ScenarioDamageCommand",
                "ScenarioMakeItemBoxCommand",
                "ScenarioEatItemBoxCommand",
                "ScenarioTeamGaugeCommand",
                "ScenarioUseBonusCommand"
            ),
            types
        );
        var power = codec.decode(MatchFixtures.root("native-cannon-power"));
        var eaten = (ScenarioEatItemBoxCommand) power.scenario().commands().get(2);
        assertEquals(360850920143060993L, eaten.entityId());
        assertEquals(4, eaten.itemId());
        var bonus = codec.decode(MatchFixtures.root("equipment-perks-kind15-fixtures-kind15-slot2-damage10.synthetic"));
        assertEquals(1010, ((ScenarioUseBonusCommand) bonus.scenario().commands().getFirst()).itemId());
        var fish = codec.decode(MatchFixtures.root("weapon-effects-fix-scenario-fish-native"));
        assertEquals(.5f, fish.scenario().effects().getFirst().param1());
        assertEquals(2, fish.scenario().effects().getFirst().availableOwnTurns());
    }
}
