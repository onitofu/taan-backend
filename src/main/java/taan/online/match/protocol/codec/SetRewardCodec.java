package taan.online.match.protocol.codec;

import java.util.ArrayList;
import taan.online.match.protocol.packet.GameRewardExp;
import taan.online.match.protocol.packet.PcResultSetReward;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;

public final class SetRewardCodec {
    public byte[] encode(PcResultSetReward message) {
        requireCount(message.rewards().size());
        var output = new BinaryWriter().unsignedInt(PcResultSetReward.TYPE_ID).unsignedInt(8);

        for (var reward : message.rewards()) {
            output.unsignedInt(GameRewardExp.TYPE_ID).wideString(reward.label()).unsignedByte(3)
                .unsignedInt(reward.characterId()).signedInt(reward.amount());
        }

        return output.toByteArray();
    }

    public PcResultSetReward decode(byte[] root) {
        var input = new BinaryReader(root, PcResultSetReward.TYPE_ID);
        requireCount(input.unsignedInt());
        var rewards = new ArrayList<GameRewardExp>();

        for (int index = 0; index < 8; index++) {
            if (input.unsignedInt() != GameRewardExp.TYPE_ID) {
                throw new MalformedPacketException("UNEXPECTED_REWARD_TYPE");
            }

            String label = input.wideString();

            if (input.unsignedByte() != 3) {
                throw new MalformedPacketException("UNEXPECTED_REWARD_KIND");
            }

            rewards.add(new GameRewardExp(label, input.unsignedInt(), input.signedInt()));
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
