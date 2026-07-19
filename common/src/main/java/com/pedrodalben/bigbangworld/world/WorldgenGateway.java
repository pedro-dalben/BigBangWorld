package com.pedrodalben.bigbangworld.world;

import com.pedrodalben.bigbangworld.domain.WorldDefinition;
import com.pedrodalben.bigbangworld.domain.WorldType;
import com.pedrodalben.bigbangworld.util.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Applies integrations that must exist before a managed dimension generates chunks. */
public final class WorldgenGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorldgenGateway.class);
    private static final String RAID_MOD = "cobblemonraiddens";
    private static final String RAID_CLASS = "com.necro.raid.dens.common.CobblemonRaidDens";
    private static final String OVERWORLD = "minecraft:overworld";

    private static volatile Set<String> raidDimensions = Set.of();

    private WorldgenGateway() {}

    public static void prepare(Iterable<WorldDefinition> activeWorlds) {
        syncRaidDens(activeWorlds);
    }

    public static boolean isNormal(WorldDefinition def) {
        return def != null && def.getType() == WorldType.NORMAL;
    }

    public static boolean isRaidDensEnabled(WorldDefinition def) {
        return def != null && raidDimensions.contains(dimensionKey(def));
    }

    public static boolean isRepurposedStructuresLoaded() {
        return isModLoaded("repurposed_structures");
    }

    public static boolean isLegendaryMonumentsLoaded() {
        return isModLoaded("legendarymonuments");
    }

    private static void syncRaidDens(Iterable<WorldDefinition> activeWorlds) {
        if (!isModLoaded(RAID_MOD)) {
            raidDimensions = Set.of();
            return;
        }

        try {
            Class<?> modClass = Class.forName(RAID_CLASS);
            Object config = modClass.getField("CONFIG").get(null);
            if (config == null) {
                raidDimensions = Set.of();
                LOGGER.debug("[BigBangWorld] CobblemonRaidDens config is not ready yet");
                return;
            }

            Map<String, double[]> tierWeights = mapField(config, "dimension_tier_weights");
            Map<String, Integer> spawnRates = mapField(config, "dimension_spawn_rate");
            double[] overworldWeights = copyWeights(tierWeights.get(OVERWORLD));
            int overworldRate = spawnRates.getOrDefault(OVERWORLD, 256);
            Set<String> configured = new HashSet<>();

            for (WorldDefinition def : activeWorlds) {
                String key = dimensionKey(def);
                tierWeights.putIfAbsent(key, overworldWeights.clone());
                spawnRates.putIfAbsent(key, overworldRate);
                configured.add(key);
            }

            raidDimensions = Set.copyOf(configured);
            LOGGER.info("[BigBangWorld] CobblemonRaidDens enabled for {} managed dimensions", configured.size());
        } catch (ReflectiveOperationException | RuntimeException e) {
            raidDimensions = Set.of();
            LOGGER.warn("[BigBangWorld] Could not integrate with CobblemonRaidDens; continuing without integration", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getField(name);
        Map<K, V> value = (Map<K, V>) field.get(target);
        if (value == null) {
            value = new HashMap<>();
            field.set(target, value);
        }
        return value;
    }

    private static double[] copyWeights(double[] weights) {
        return weights == null || weights.length != 7
                ? new double[] {9.0, 15.0, 25.0, 25.0, 20.0, 5.0, 1.0}
                : weights.clone();
    }

    private static String dimensionKey(WorldDefinition def) {
        return def.getDimensionKey() == null || def.getDimensionKey().isBlank()
                ? "bigbangworld:" + def.getId()
                : def.getDimensionKey();
    }

    private static boolean isModLoaded(String modId) {
        try {
            return Platform.isModLoaded(modId);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
