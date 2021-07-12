package xyz.nucleoid.plasmid.game.stats;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import xyz.nucleoid.plasmid.util.PlayerRef;

import java.util.Map;
import java.util.UUID;

/**
 * Wrapper containing a map players' {@link StatisticMap}s
 */
public class GameStatisticBundle {
    private final Object2ObjectMap<UUID, StatisticMap> players = new Object2ObjectOpenHashMap<>();
    private final StatisticMap global = new StatisticMap();

    public StatisticMap forPlayer(PlayerRef player) {
        return this.forPlayer(player.getId());
    }

    public StatisticMap forPlayer(ServerPlayerEntity player) {
        return this.forPlayer(player.getUuid());
    }

    public StatisticMap forPlayer(UUID uuid) {
        return this.players.computeIfAbsent(uuid, __ -> new StatisticMap());
    }

    public StatisticMap global() {
        return this.global;
    }

    public boolean isEmpty() {
        return this.global.isEmpty()
                && (this.players.isEmpty() || this.players.values().stream().allMatch(StatisticMap::isEmpty));
    }

    public JsonObject encodeBundle() {
        JsonObject obj = new JsonObject();
        JsonObject players = new JsonObject();
        for (Map.Entry<UUID, StatisticMap> entry : this.players.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                players.add(entry.getKey().toString(), entry.getValue().encodeBundle());
            }
        }

        obj.add("players", players);

        if (!this.global.isEmpty()) {
            obj.add("global", this.global.encodeBundle());
        }
        return obj;
    }
}
