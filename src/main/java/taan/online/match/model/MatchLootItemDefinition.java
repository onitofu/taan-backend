package taan.online.match.model;

record MatchLootItemDefinition(int itemId, int minScalar, int maxScalar, int minWeight, int maxWeight) {
    MatchLootItemDefinition {
        if (itemId < 1 || itemId > 65535 || minScalar < 0 || maxScalar > 100 || minScalar >= maxScalar
            || minWeight < 1 || maxWeight < 1) {
            throw new IllegalArgumentException("Invalid native loot item definition");
        }
    }

    int weight(int scalar) {
        if (scalar < minScalar || scalar > maxScalar) {
            return 0;
        }

        return minWeight + (int) ((long) (maxWeight - minWeight) * (scalar - minScalar) / (maxScalar - minScalar));
    }
}
