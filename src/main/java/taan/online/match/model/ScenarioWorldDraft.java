package taan.online.match.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ScenarioWorldDraft {
    private final MatchWorldState original;
    private final MatchDefinitions definitions;
    private final Map<UUID, MatchParticipant> participants;
    private final Map<UUID, MatchActorState> actors;
    private final Map<UUID, List<MatchFall>> falls = new HashMap<>();
    private final Set<UUID> deaths;
    private final Map<UUID, Integer> energyMaxima = new HashMap<>();
    private final ScenarioTerrainDraft terrain;
    private final Map<Long, MatchItemBox> boxes;
    private final int boxBudget;
    private int creations;
    private MatchCombatStatistics combatStatistics;

    public ScenarioWorldDraft(MatchWorldState original, MatchDefinitions definitions, List<MatchParticipant> roster) {
        this(original, definitions, roster, 0);
    }

    public ScenarioWorldDraft(
        MatchWorldState original,
        MatchDefinitions definitions,
        List<MatchParticipant> roster,
        int boxBudget
    ) {
        this.original = original;
        this.definitions = definitions;
        participants = roster.stream().collect(Collectors.toUnmodifiableMap(
            MatchParticipant::sessionId,
            value -> value
        ));
        actors = new HashMap<>(original.actors());
        deaths = new HashSet<>(original.processedDeaths());
        terrain = new ScenarioTerrainDraft(original.terrain());
        boxes = new HashMap<>(original.boxes());
        this.boxBudget = boxBudget;
        combatStatistics = original.combatStatistics();
    }

    public void fired(UUID session) {
        combatStatistics = combatStatistics.fired(session);
    }

    public void finishShot(UUID session) {
        if (combatStatistics.damage(session) > original.combatStatistics().damage(session)) {
            combatStatistics = combatStatistics.hit(session);
        }
    }

    public void createBox(long id, int x, int y) {
        var box = MatchBoxRules.create(boxes, definitions.map(), boxBudget, creations, id, x, y);
        boxes.put(id, box);
        creations++;
    }

    public void pickupBox(long id, int itemId) {
        var box = boxes.get(id);

        if (box == null) {
            throw new ScenarioWorldException("UNKNOWN_BOX");
        }

        boxes.put(id, box.pickedUp(itemId));
    }

    public void grantEnergy(UUID session, int delta) {
        var actor = actor(session);
        int energy = MatchEnergyRules.adjust(actor.energy(), delta, participants.get(session).maxEnergy());
        actors.put(session, new MatchActorState(actor.x(), actor.y(), actor.facing(), actor.hp(), energy));
    }

    public void damageEnergy(UUID session, int contribution) {
        actor(session);
        energyMaxima.compute(
            session,
            (ignored, previous) -> MatchEnergyRules.maximum(
                previous == null ? 0 : previous,
                contribution
            )
        );
    }

    public void damage(UUID session, int delta) {
        if (delta < 0) {
            throw new ScenarioWorldException("INVALID_DAMAGE");
        }

        var actor = actor(session);
        int hp = Math.clamp((long) actor.hp() - delta, 0, participants.get(session).maxHp());
        actors.put(session, new MatchActorState(actor.x(), actor.y(), actor.facing(), hp, actor.energy()));
    }

    public void damage(UUID source, UUID target, int delta) {
        actor(source);
        int before = actor(target).hp();
        damage(target, delta);

        if (participants.get(source).team() != participants.get(target).team()) {
            combatStatistics = combatStatistics.credit(source, before - actor(target).hp());
        }

        if (before > 0 && actor(target).dead()) {
            dead(source, target);
        }
    }

    public void dead(UUID source, UUID session) {
        boolean first = !deaths.contains(session);
        dead(session);

        if (first && participants.get(source).team() != participants.get(session).team()) {
            combatStatistics = combatStatistics.killed(source);
        }
    }

    public void dead(UUID session) {
        var actor = actor(session);
        actors.put(session, new MatchActorState(actor.x(), actor.y(), actor.facing(), 0, actor.energy()));
        deaths.add(session);
    }

    public void explode(WeaponMissileKey missileTypeId, int x, int y, float factor) {
        var missile = definitions.missiles().get(missileTypeId);

        if (missile == null) {
            throw new ScenarioWorldException("UNKNOWN_MISSILE_TERRAIN");
        }

        terrain.explode(missile, x, y, factor);
    }

    public void fall(UUID session, long tick, int duration, int startY, int endY) {
        var actor = actor(session);

        if (tick < 0 || tick > 120000 || duration <= 0 || tick + duration > 120000
            || startY < 0 || endY < startY || endY > (long) definitions.map().height() + 200) {
            throw new ScenarioWorldException("INVALID_FALL");
        }

        var previous = falls.computeIfAbsent(session, ignored -> new ArrayList<>());

        if (previous.stream().anyMatch(
            old -> old.tick() <= tick && old.tick() + old.duration() + 3 >= tick && old.endY() > endY
        )) {
            return;
        }

        int currentY = at(actor.y(), previous, tick);
        previous.add(new MatchFall(participants.get(session).slotIndex(), tick, duration, startY, endY, currentY));
    }

    public MatchWorldState finish(ScenarioCommitReceipt receipt, Set<UUID> refitted, long endTick) {
        if (receipt.worldRevision() != Math.incrementExact(original.revision())) {
            throw new ScenarioWorldException("INVALID_WORLD_REVISION");
        }

        for (int pass = 0; pass < 2; pass++) {
            energyMaxima.forEach(this::grantEnergy);
        }

        var layers = terrain.freeze();
        var repositioned = new HashSet<>(refitted);
        repositioned.addAll(falls.keySet());

        for (var session : repositioned) {
            var actor = actor(session);
            int endpoint = at(actor.y(), falls.getOrDefault(session, List.of()), endTick);
            int y = MatchTerrainSupport.settledY(layers, actor.x(), endpoint, definitions.map().height());
            actors.put(session, new MatchActorState(actor.x(), y, actor.facing(), actor.hp(), actor.energy()));
        }

        return new MatchWorldState(
            receipt.worldRevision(),
            actors,
            layers,
            falls,
            deaths,
            Optional.of(receipt),
            boxes,
            combatStatistics
        );
    }

    public int changedCells() {
        return terrain.changedCells();
    }

    private MatchActorState actor(UUID session) {
        var actor = actors.get(session);

        if (actor == null || !participants.containsKey(session)) {
            throw new ScenarioWorldException("UNKNOWN_ACTOR");
        }

        return actor;
    }

    private int at(int initialY, List<MatchFall> reports, long tick) {
        int y = initialY;

        for (var report : reports) {
            if (report.tick() > tick) {
                continue;
            }

            int candidate;

            if (report.tick() + report.duration() <= tick) {
                candidate = report.endY();
            } else {
                float ratio = (float) ((tick - report.tick()) / (double) report.duration());
                candidate = report.effectiveStartY() + (int) (ratio * (report.endY() - report.effectiveStartY()));
            }

            y = Math.max(y, candidate);
        }

        return y;
    }
}
