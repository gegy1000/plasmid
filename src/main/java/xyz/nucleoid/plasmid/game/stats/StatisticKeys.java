package xyz.nucleoid.plasmid.game.stats;

import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.Plasmid;

/**
 * A set of general {@link StatisticKey}s for minigames to make use of
 *
 * @see StatisticKey
 */
public final class StatisticKeys {
    // TODO: What StorageTypes should these all be?

    // Games
    public static final StatisticKey<Integer> GAMES_PLAYED = StatisticKey.intKey(id("games_played"), StatisticKey.StorageType.TOTAL);
    public static final StatisticKey<Integer> GAMES_WON = StatisticKey.intKey(id("games_won"), StatisticKey.StorageType.TOTAL);
    public static final StatisticKey<Integer> GAMES_LOST = StatisticKey.intKey(id("games_lost"), StatisticKey.StorageType.TOTAL);

    // PvP / PvE;
    public static final StatisticKey<Integer> KILLS = StatisticKey.intKey(id("total_kills"), StatisticKey.StorageType.TOTAL);
    public static final StatisticKey<Integer> DEATHS = StatisticKey.intKey(id("deaths"), StatisticKey.StorageType.TOTAL);
    public static final StatisticKey<Double> DAMAGE_TAKEN = StatisticKey.doubleKey(id("damage_taken"), StatisticKey.StorageType.TOTAL);
    public static final StatisticKey<Double> DAMAGE_DEALT = StatisticKey.doubleKey(id("damage_dealt"), StatisticKey.StorageType.TOTAL);

    // Misc;
    public static final StatisticKey<Integer> BLOCKS_BROKEN = StatisticKey.intKey(id("blocks_broken"), StatisticKey.StorageType.TOTAL);
    public static final StatisticKey<Integer> POINTS = StatisticKey.intKey(id("points"), StatisticKey.StorageType.TOTAL);
    public static final StatisticKey<Integer> TIME_SURVIVED = StatisticKey.intKey(id("time_survived"), StatisticKey.StorageType.TOTAL);
    public static final StatisticKey<Integer> RANKING = StatisticKey.intKey(id("ranking"), StatisticKey.StorageType.TOTAL);

    private static Identifier id(String path) {
        return new Identifier(Plasmid.ID, path);
    }
    private StatisticKeys() { }
}
