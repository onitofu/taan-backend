package taan.online;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

public final class WeaponExpansionFixtures {
    private static final String DIRECTORY = "/match/weapon-expansion-1-2-6/";

    private WeaponExpansionFixtures() {
    }

    public static Stream<WeaponExpansionCase> cases() throws IOException {
        try (var input = WeaponExpansionFixtures.class.getResourceAsStream(DIRECTORY + "cases.tsv")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().skip(1).map(line -> {
                var columns = line.split("\t", -1);
                return new WeaponExpansionCase(
                    columns[0],
                    columns[1],
                    columns[2],
                    Integer.parseInt(columns[3]),
                    Integer.parseInt(columns[4]),
                    Integer.parseInt(columns[5]),
                    Integer.parseInt(columns[6]),
                    columns[7].isEmpty() ? List.of() : Arrays.stream(columns[7].split(","))
                        .map(Integer::parseInt).toList(),
                    columns[8],
                    columns[9],
                    columns[10],
                    Integer.parseInt(columns[11]),
                    Boolean.parseBoolean(columns[12])
                );
            }).toList().stream();
        }
    }

    public static byte[] root(WeaponExpansionCase evidence) throws IOException {
        try (var input = WeaponExpansionFixtures.class.getResourceAsStream(DIRECTORY + evidence.filename())) {
            return HexFormat.of().parseHex(
                new String(input.readAllBytes(), StandardCharsets.US_ASCII).replaceAll("\\s+", "")
            );
        }
    }
}
