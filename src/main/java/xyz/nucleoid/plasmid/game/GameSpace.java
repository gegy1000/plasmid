package xyz.nucleoid.plasmid.game;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.stats.GameStatisticBundle;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Represents the space within which a game occurs through attached {@link GameLogic}
 */
public interface GameSpace extends AutoCloseable {
    /**
     * Swaps out the active {@link GameLogic} within this {@link GameSpace}.
     *
     * @param builder the builder to apply on the newly constructed {@link GameLogic}
     */
    void openGame(Consumer<GameLogic> builder);

    CompletableFuture<StartResult> requestStart();

    /**
     * Attempts to remove the given {@link ServerPlayerEntity} from this {@link GameSpace}.
     * When a player is removed, they will be teleported back to their former location prior to joining
     *
     * @param player {@link ServerPlayerEntity} to remove from this {@link GameSpace}
     * @return whether the {@link ServerPlayerEntity} was successfully removed
     */
    boolean removePlayer(ServerPlayerEntity player);

    /**
     * Closes this game with a reason
     *
     * @param reason the reason for this game closing
     */
    void close(GameCloseReason reason);

    /**
     * @deprecated use {@link GameSpace#close(GameCloseReason)} with reason
     */
    @Override
    @Deprecated
    default void close() {
        this.close(GameCloseReason.FINISHED);
    }

    /**
     * Adds a resource to this {@link GameSpace} object that will be automatically closed when this {@link GameSpace}
     * is closed.
     *
     * This differs from {@link GameLogic#addResource(AutoCloseable)}, which will be closed when the {@link GameLogic}
     * instance is closed.
     *
     * @param resource the resource to close when this {@link GameSpace} closes
     * @return the added resource
     */
    <T extends AutoCloseable> T addResource(T resource);

    /**
     * Returns all {@link ServerPlayerEntity}s in this {@link GameSpace}.
     *
     * <p>{@link GameSpace#containsPlayer(ServerPlayerEntity)} can be used to check if a {@link ServerPlayerEntity} is in this {@link GameSpace} instead.
     *
     * @return a {@link PlayerSet} that contains all {@link ServerPlayerEntity}s in this {@link GameSpace}
     */
    PlayerSet getPlayers();

    /**
     * @return the number of players in this {@link GameSpace}.
     */
    default int getPlayerCount() {
        return this.getPlayers().size();
    }

    /**
     * Returns whether this {@link GameSpace} contains the given {@link ServerPlayerEntity}.
     *
     * @param player {@link ServerPlayerEntity} to check existence of
     * @return whether the given {@link ServerPlayerEntity} exists in this {@link GameSpace}
     */
    default boolean containsPlayer(ServerPlayerEntity player) {
        return this.getPlayers().contains(player);
    }

    /**
     * Returns whether this {@link GameSpace} contains the given {@link Entity}.
     *
     * @param entity {@link Entity} to check existence of
     * @return whether the given {@link Entity} exists in this {@link GameSpace}
     */
    default boolean containsEntity(Entity entity) {
        return this.getWorld().getEntity(entity.getUuid()) != null;
    }

    /**
     * Returns the {@link ServerWorld} that this {@link GameSpace} is hosted in.
     *
     * @return the host world of this {@link GameSpace}.
     */
    ServerWorld getWorld();

    /**
     * @return the host server of this {@link GameSpace}
     */
    default MinecraftServer getServer() {
        return this.getWorld().getServer();
    }

    /**
     * @return the game config that is running within this {@link GameSpace}
     */
    ConfiguredGame<?> getGameConfig();

    /**
     * @return the game config that created this {@link GameSpace}
     */
    ConfiguredGame<?> getSourceGameConfig();

    /**
     * @return the lifecycle manager for this {@link GameSpace}
     */
    GameLifecycle getLifecycle();

    /**
     * @param namespace The statistic namespace to get a bundle for
     * @return the {@link GameStatisticBundle} for the given namespace
     */
    GameStatisticBundle getStatistics(String namespace);

    /**
     * @param consumer Will be called for every non-empty {@link GameStatisticBundle} in this {@link GameSpace}
     */
    void visitAllStatistics(BiConsumer<String, GameStatisticBundle> consumer);
}
