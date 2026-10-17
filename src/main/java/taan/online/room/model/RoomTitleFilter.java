package taan.online.room.model;

import java.util.List;
import java.util.Locale;

public final class RoomTitleFilter {
    private final List<String> forbidden;

    public RoomTitleFilter(List<String> forbidden) {
        this.forbidden = forbidden.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    public boolean forbids(RoomTitle title) {
        String value = title.value().toLowerCase(Locale.ROOT);
        return forbidden.stream().anyMatch(value::contains);
    }
}
