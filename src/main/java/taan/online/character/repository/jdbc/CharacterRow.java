package taan.online.character.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import taan.online.account.model.AccountGender;
import taan.online.account.model.AccountId;
import taan.online.character.model.AppearancePart;
import taan.online.character.model.CharacterAppearance;
import taan.online.character.model.CharacterId;
import taan.online.character.model.CharacterName;
import taan.online.character.model.GameCharacter;

final class CharacterRow {
    private final CharacterId id;
    private final AccountId owner;
    private final CharacterName name;
    private final int level;
    private final AccountGender gender;
    private final Instant createdAt;
    private final List<AppearancePart> parts = IntStream.range(0, 16)
        .mapToObj(slot -> new AppearancePart(slot, 0, 0, 0, 0)).collect(Collectors.toCollection(ArrayList::new));

    CharacterRow(ResultSet rows) throws SQLException {
        if (rows.getObject("equipment_id") == null) {
            throw new SQLException("Missing character equipment inventory", "22000");
        }

        id = new CharacterId(rows.getLong("id"));
        owner = new AccountId(rows.getLong("account_id"));
        name = new CharacterName(rows.getString("name"));
        level = rows.getInt("level");
        gender = gender(rows.getShort("gender"));
        createdAt = rows.getObject("created_at", OffsetDateTime.class).toInstant();
    }

    void addPart(ResultSet rows) throws SQLException {
        if (rows.getObject("slot") != null) {
            var part = new AppearancePart(
                rows.getInt("slot"),
                rows.getLong("item_id"),
                rows.getInt("palette_1_id"),
                rows.getInt("palette_2_id"),
                rows.getInt("palette_3_id")
            );
            parts.set(part.slot(), part);
        }
    }

    GameCharacter character() {
        return new GameCharacter(id, owner, name, level, gender, new CharacterAppearance(parts), createdAt);
    }

    static AccountGender gender(short value) throws SQLException {
        return switch (value) {
            case 1 -> AccountGender.MALE;
            case 2 -> AccountGender.FEMALE;
            default -> throw new SQLException("Unsupported stored account gender", "22000");
        };
    }
}
