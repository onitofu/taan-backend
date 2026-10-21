package taan.online.match.protocol.codec;

import java.util.ArrayList;
import taan.online.match.protocol.packet.GameReward;
import taan.online.match.protocol.packet.GameRewardExp;
import taan.online.match.protocol.packet.GameRewardGold;
import taan.online.match.protocol.packet.GameRewardQuest;
import taan.online.match.protocol.packet.PcResultSetReward;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class SetRewardCodec {
    public byte[] encode(PcResultSetReward message) {
        requireCount(message.rewards().size());
        var output = new BinaryWriter().unsignedInt(PcResultSetReward.TYPE_ID).unsignedInt(8);

        for (var reward : message.rewards()) {
            int type = switch (reward) {
                case GameRewardExp ignored -> GameRewardExp.TYPE_ID;
                case GameRewardGold ignored -> GameRewardGold.TYPE_ID;
                case GameRewardQuest ignored -> GameRewardQuest.TYPE_ID;
            };
            int kind = reward instanceof GameRewardExp ? 3 : reward instanceof GameRewardGold ? 4 : 1;
            output.unsignedInt(type).wideString(reward.label()).unsignedByte(kind).unsignedInt(reward.characterId());

            switch (reward) {
                case GameRewardExp exp -> output.signedInt(Math.toIntExact(exp.amount()));
                case GameRewardGold gold -> output.unsignedInt(gold.amount()).unsignedByte(gold.hasExtraCard())
                    .unsignedInt(gold.extraCardAmount());
                case GameRewardQuest quest -> output.unsignedInt(quest.value());
            }
        }

        return output.toByteArray();
    }

    public PcResultSetReward decode(byte[] root) {
        var input = new BinaryReader(root, PcResultSetReward.TYPE_ID);
        requireCount(input.unsignedInt());
        var rewards = new ArrayList<GameReward>();

        for (int index = 0; index < 8; index++) {
            long type = input.unsignedInt();
            String label = input.wideString();
            int kind = input.unsignedByte();
            long character = input.unsignedInt();
            GameReward reward;

            if (type == GameRewardExp.TYPE_ID && kind == 3) {
                reward = new GameRewardExp(label, character, input.signedInt());
            } else if (type == GameRewardGold.TYPE_ID && kind == 4) {
                reward = new GameRewardGold(
                    label,
                    character,
                    input.unsignedInt(),
                    input.unsignedByte(),
                    input.unsignedInt()
                );
            } else if (type == GameRewardQuest.TYPE_ID && kind == 1) {
                reward = new GameRewardQuest(label, character, input.unsignedInt());
            } else {
                throw new MalformedPacketException("UNEXPECTED_REWARD_TYPE_OR_KIND");
            }

            rewards.add(reward);
        }

        input.finish();
        return new PcResultSetReward(rewards);
    }

    private static void requireCount(long count) {
        if (count != 8) {
            throw new MalformedPacketException("INVALID_REWARD_COUNT");
        }
    }
}
