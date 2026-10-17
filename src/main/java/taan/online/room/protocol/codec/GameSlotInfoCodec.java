package taan.online.room.protocol.codec;

import taan.online.character.protocol.codec.CharacterAppearCodec;
import taan.online.protocol.BinaryReader;
import taan.online.protocol.BinaryWriter;
import taan.online.protocol.MalformedPacketException;
import taan.online.room.protocol.packet.GameSlotInfo;
import taan.online.room.protocol.packet.GameSlotOccupant;

public final class GameSlotInfoCodec {
    public GameSlotInfo decode(BinaryReader input) {
        if (input.unsignedInt() != Integer.toUnsignedLong(GameSlotInfo.TYPE_ID)) {
            throw new MalformedPacketException("UNEXPECTED_ROOM_SLOT_TYPE");
        }

        int index = input.unsignedByte();
        int state = input.unsignedByte();
        validate(index, state);

        if (state <= 1) {
            return new GameSlotInfo(index, state, null);
        }

        long account = input.unsignedInt();
        long character = input.unsignedInt();
        String name = input.wideString();
        String affiliation = input.wideString();
        int level = input.unsignedShort();
        int team = input.unsignedByte();
        var appearance = input.unsignedByte() == 0 ? null : new CharacterAppearCodec().decode(input);
        int weapon = input.unsignedShort();

        if (input.unsignedByte() != 0) {
            throw new MalformedPacketException("UNSUPPORTED_ROOM_SLOT_EXTRA");
        }

        return new GameSlotInfo(
            index,
            state,
            new GameSlotOccupant(account, character, name, affiliation, level, team, appearance, weapon)
        );
    }

    public void encode(BinaryWriter output, GameSlotInfo slot) {
        validate(slot.slotIndex(), slot.state());

        if ((slot.state() > 1) != (slot.occupant() != null)) {
            throw new MalformedPacketException("INCONSISTENT_ROOM_SLOT_FIELDS");
        }

        output.unsignedInt(GameSlotInfo.TYPE_ID).unsignedByte(slot.slotIndex()).unsignedByte(slot.state());

        if (slot.state() <= 1) {
            return;
        }

        var person = slot.occupant();
        output.unsignedInt(person.accountId()).unsignedInt(person.characterId()).wideString(person.name())
            .wideString(person.affiliationText()).unsignedShort(person.level()).unsignedByte(person.team())
            .unsignedByte(person.appearance() == null ? 0 : 1);

        if (person.appearance() != null) {
            new CharacterAppearCodec().encode(output, person.appearance());
        }

        output.unsignedShort(person.weaponId()).unsignedByte(0);
    }

    private void validate(int index, int state) {
        if (index < 0 || index > 7 || state < 0 || state > 3) {
            throw new MalformedPacketException("INVALID_ROOM_SLOT");
        }
    }
}
