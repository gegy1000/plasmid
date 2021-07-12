package xyz.nucleoid.plasmid.game;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.fantasy.BubbleWorldConfig;
import xyz.nucleoid.fantasy.BubbleWorldHandle;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.WorldPlayerListener;
import xyz.nucleoid.leukocyte.Leukocyte;
import xyz.nucleoid.leukocyte.authority.Authority;
import xyz.nucleoid.leukocyte.shape.ProtectionShape;
import xyz.nucleoid.plasmid.error.ErrorReporter;
import xyz.nucleoid.plasmid.event.GameEvents;
import xyz.nucleoid.plasmid.game.event.*;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.player.MutablePlayerSet;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.game.stats.GameStatisticBundle;
import xyz.nucleoid.plasmid.util.Scheduler;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Represents the space within which a game occurs through attached {@link GameLogic}
 *
 * <p>Each world has a {@link net.minecraft.world.gen.chunk.ChunkGenerator} which is invoked as chunks are requested.
 * It is important to note that not all chunks will be loaded on start, and the game logic must take care to handle this.
 * Players can only be added to this game space through {@link ManagedGameSpace#addPlayer} or {@link ManagedGameSpace#offerPlayer}.
 */
public final class ManagedGameSpace implements GameSpace {
    private static final Logger LOGGER = LogManager.getLogger(ManagedGameSpace.class);

    private static final Map<RegistryKey<World>, ManagedGameSpace> DIMENSION_TO_WORLD = new Reference2ObjectOpenHashMap<>();

    private final BubbleWorldHandle bubble;
    private final ConfiguredGame<?> gameConfig;
    private final ConfiguredGame<?> sourceGameConfig;

    private final MutablePlayerSet players;

    private final GameLogic emptyLogic = new GameLogic(this);
    private final AtomicReference<GameLogic> logic = new AtomicReference<>(this.emptyLogic);

    private final GameResources resources = new GameResources();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final GameLifecycle lifecycle = new GameLifecycle();

    private final Authority ruleAuthority;

    private final ErrorReporter errorReporter;

    private final Object2ObjectMap<String, GameStatisticBundle> statistics = new Object2ObjectOpenHashMap<>();

    private ManagedGameSpace(MinecraftServer server, BubbleWorldHandle bubble, ConfiguredGame<?> gameConfig, ConfiguredGame<?> sourceGameConfig) {
        this.bubble = bubble;
        this.gameConfig = gameConfig;
        this.sourceGameConfig = sourceGameConfig;

        this.players = new MutablePlayerSet(server);

        Leukocyte leukocyte = Leukocyte.get(server);

        ProtectionShape ruleScope = ProtectionShape.dimension(bubble.asWorld().getRegistryKey());
        this.ruleAuthority = Authority.createTransient().addShape("game", ruleScope);
        this.ruleAuthority.exclusions.includeOperators();

        leukocyte.addAuthority(this.ruleAuthority);

        bubble.addPlayerListener(new WorldPlayerListener() {
            @Override
            public void onRemovePlayer(ServerPlayerEntity player) {
                ManagedGameSpace.this.onRemovePlayer(player);
            }
        });

        this.errorReporter = ErrorReporter.open(this.gameConfig);
    }

    /**
     * Attempts to open a new {@link GameSpace} using the given {@link BubbleWorldConfig}.
     *
     * <p>If a {@link GameSpace} could not be opened, a {@link GameOpenException} is thrown.
     *
     * @param server {@link MinecraftServer} to open a {@link GameSpace} in
     * @param game initial game to open this {@link GameSpace} with
     * @param config {@link BubbleWorldConfig} to use to construct a {@link GameSpace}
     * @return a future to a {@link GameSpace} if one was opened
     * @throws GameOpenException if a {@link GameSpace} could not be opened
     */
    public static CompletableFuture<ManagedGameSpace> open(MinecraftServer server, ConfiguredGame<?> game, BubbleWorldConfig config) {
        return open(server, game, game, config);
    }

    /**
     * Attempts to open a new {@link GameSpace} using the given {@link BubbleWorldConfig}.
     *
     * <p>If a {@link GameSpace} could not be opened, a {@link GameOpenException} is thrown.
     *
     * @param server {@link MinecraftServer} to open a {@link GameSpace} in
     * @param game game to open this {@link GameSpace} with
     * @param sourceGame the game that opened this {@link GameSpace}
     * @param config {@link BubbleWorldConfig} to use to construct a {@link GameSpace}
     * @return a future to a {@link GameSpace} if one was opened
     * @throws GameOpenException if a {@link GameSpace} could not be opened
     */
    public static CompletableFuture<ManagedGameSpace> open(MinecraftServer server, ConfiguredGame<?> game, ConfiguredGame<?> sourceGame, BubbleWorldConfig config) {
        return Fantasy.get(server).openBubble(config).thenApply(bubble -> {
            bubble.setTickWhenEmpty(false);

            ManagedGameSpace gameSpace = new ManagedGameSpace(server, bubble, game, sourceGame);
            DIMENSION_TO_WORLD.put(bubble.asWorld().getRegistryKey(), gameSpace);

            return gameSpace;
        });
    }

    /**
     * Returns the {@link ManagedGameSpace} hosted in the given {@link World}.
     *
     * <p>If a {@link ManagedGameSpace} does not exist in the given {@link World}, null is returned.
     *
     * @param world world to check for a {@link ManagedGameSpace}
     * @return the {@link ManagedGameSpace} hosted in the given {@link World}, or null if none are found
     */
    @Nullable
    public static ManagedGameSpace forWorld(World world) {
        return DIMENSION_TO_WORLD.get(world.getRegistryKey());
    }

    /**
     * Returns a {@link Collection} of open {@link ManagedGameSpace}s across all dimensions.
     *
     * @return all open {@link ManagedGameSpace} instances
     */
    public static Collection<ManagedGameSpace> getOpen() {
        return DIMENSION_TO_WORLD.values();
    }

    @Override
    public void openGame(Consumer<GameLogic> builder) {
        if (this.closed.get()) {
            return;
        }

        GameLogic logic = new GameLogic(this);
        builder.accept(logic);
        GameEvents.SET_LOGIC.invoker().onSetGameLogic(logic, this);

        Scheduler.INSTANCE.submit(server -> {
            logic.setAuthority(this.ruleAuthority);

            GameLogic closedGameLogic = this.logic.getAndSet(logic);

            closedGameLogic.getResources().close();

            try {
                closedGameLogic.getListeners().invoker(GameCloseListener.EVENT).onClose();
            } catch (Exception e) {
                LOGGER.error("An unexpected exception occurred while closing the game", e);
            }

            List<ServerPlayerEntity> playersToJoin = Lists.newArrayList(this.players);
            for (ServerPlayerEntity player : playersToJoin) {
                try {
                    this.invoker(PlayerAddListener.EVENT).onAddPlayer(player);
                } catch (Throwable t) {
                    LOGGER.error("An unexpected exception occurred while adding {} to game", player.getDisplayName(), t);
                    player.sendMessage(new TranslatableText("text.plasmid.event.join.error").formatted(Formatting.RED), false);

                    this.reportError(t, "Adding player");

                    this.removePlayer(player);
                }
            }

            try {
                this.invoker(GameOpenListener.EVENT).onOpen();
            } catch (Throwable t) {
                LOGGER.error("An unexpected exception occurred while opening the game", t);
                this.reportError(t, "Opening game");

                this.closeWithError("An unexpected error occurred while opening the game");
            }
        });
    }

    @Override
    public <T extends AutoCloseable> T addResource(T resource) {
        return this.resources.add(resource);
    }

    /**
     * Attempts to add the given {@link ServerPlayerEntity} to this game space if it is not already added.
     *
     * <p>{@link ManagedGameSpace#offerPlayer} can be used instead to check with {@link OfferPlayerListener} listeners before adding the player.
     *
     * @param player {@link ServerPlayerEntity} to add to this {@link ManagedGameSpace}
     * @return whether the {@link ServerPlayerEntity} was successfully added
     */
    public boolean addPlayer(ServerPlayerEntity player) {
        if (this.closed.get()) {
            return false;
        }

        if (this.bubble.addPlayer(player)) {
            this.players.add(player);
            try {
                this.invoker(PlayerAddListener.EVENT).onAddPlayer(player);
            } catch (Throwable t) {
                LOGGER.error("An unexpected exception occurred while adding {} to game", player.getDisplayName(), t);
                player.sendMessage(new TranslatableText("text.plasmid.event.add.error").formatted(Formatting.RED), false);

                this.reportError(t, "Adding player");

                this.removePlayer(player);

                return false;
            }

            this.lifecycle.addPlayer(this, player);

            return true;
        }

        return false;
    }

    @Override
    public boolean removePlayer(ServerPlayerEntity player) {
        if (this.closed.get()) {
            return false;
        }
        return this.bubble.removePlayer(player);
    }

    private void onRemovePlayer(ServerPlayerEntity player) {
        try {
            this.invoker(PlayerRemoveListener.EVENT).onRemovePlayer(player);
        } catch (Throwable t) {
            LOGGER.error("An unexpected exception occurred while removing {} from game", player.getDisplayName(), t);

            this.reportError(t, "Removing player");
        }

        this.players.remove(player);
        this.lifecycle.removePlayer(this, player);

        if (this.getPlayerCount() <= 0) {
            this.close(GameCloseReason.CANCELED);
        }
    }

    @Override
    public CompletableFuture<StartResult> requestStart() {
        return Scheduler.INSTANCE.submit(server -> {
            try {
                StartResult startResult = this.invoker(RequestStartListener.EVENT).requestStart();
                return GameEvents.START_REQUEST.invoker().onRequestStart(this, startResult);
            } catch (Throwable t) {
                LOGGER.error("An unexpected exception occurred while requesting start", t);
                this.reportError(t, "Requesting start");

                return StartResult.error(new TranslatableText("text.plasmid.game.start_result.error"));
            }
        });
    }

    /**
     * Attempts to add a {@link ServerPlayerEntity} to this {@link ManagedGameSpace} by invoking any {@link OfferPlayerListener#EVENT} listeners attached to it.
     *
     * <p>If the event succeeds, {@link JoinResult#ok()} is returned. If the player could not be added to this game space,
     * a {@link JoinResult} error is returned. If any listeners error, its response is returned and the player is not added to this game space.
     *
     * @param player the {@link ServerPlayerEntity} trying to join this {@link ManagedGameSpace}
     * @return {@link JoinResult} describing the results of the given {@link ServerPlayerEntity} trying to join
     */
    public CompletableFuture<JoinResult> offerPlayer(ServerPlayerEntity player) {
        return Scheduler.INSTANCE.submit(server -> {
            if (ManagedGameSpace.forWorld(player.world) != null) {
                return JoinResult.inOtherGame();
            }

            JoinResult result;

            try {
                result = this.invoker(OfferPlayerListener.EVENT).offerPlayer(player);
            } catch (Throwable t) {
                LOGGER.error("An unexpected exception occurred while offering {} to game", player.getDisplayName(), t);
                this.reportError(t, "Offering player");

                result = JoinResult.err(new TranslatableText("text.plasmid.join_result.error"));
            }

            if (result.isError()) {
                return result;
            }

            if (this.addPlayer(player)) {
                Text joinMessage = new TranslatableText("text.plasmid.game.join", player.getDisplayName())
                        .formatted(Formatting.YELLOW);

                this.getPlayers().sendMessage(joinMessage);

                return JoinResult.ok();
            } else {
                return JoinResult.alreadyJoined();
            }
        });
    }

    /**
     * Closes this {@link GameSpace}.
     *
     * <p>Upon close, all players in this {@link GameSpace} are removed and restored to their state prior to entering this game space.
     */
    @Override
    public void close(GameCloseReason reason) {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }

        Scheduler.INSTANCE.submit(server -> {
            try {
                GameEvents.CLOSING.invoker().onGameSpaceClosing(this, reason);

                this.lifecycle.onClosing(this, reason);

                List<ServerPlayerEntity> players = Lists.newArrayList(this.players);
                for (ServerPlayerEntity player : players) {
                    this.bubble.removePlayer(player);
                }

                try {
                    this.invoker(GameCloseListener.EVENT).onClose();
                } catch (Throwable t) {
                    LOGGER.error("An unexpected exception occurred while closing the game", t);
                    this.reportError(t, "Closing game");
                }

                this.logic.get().getResources().close();
                this.resources.close();

                this.lifecycle.onClosed(this, players, reason);

                Leukocyte leukocyte = Leukocyte.get(this.getServer());
                leukocyte.removeAuthority(this.ruleAuthority);

                this.errorReporter.close();
            } finally {
                this.logic.set(this.emptyLogic);

                this.bubble.delete();
                DIMENSION_TO_WORLD.remove(this.bubble.asWorld().getRegistryKey(), this);
            }
        });
    }

    public void reportError(Throwable throwable, String context) {
        this.errorReporter.report(throwable, context);
    }

    public void closeWithError(String message) {
        this.getPlayers().sendMessage(new LiteralText(message).formatted(Formatting.RED));
        this.close(GameCloseReason.CANCELED);
    }

    @Override
    public PlayerSet getPlayers() {
        return this.players;
    }

    @NotNull
    public <T> T invoker(EventType<T> event) {
        GameLogic logic = this.logic.get();
        return logic.getListeners().invoker(event);
    }

    /**
     * Tests whether the given {@link GameRule} passes in this {@link GameSpace}.
     *
     * <p>As an example, calling this method with {@link GameRule#BLOCK_DROPS} will return a {@link RuleResult} that describes whether blocks can drop.
     *
     * @param rule the {@link GameRule} to test in this {@link GameSpace}
     * @return a {@link RuleResult} that describes whether the {@link GameRule} passes
     */
    public RuleResult testRule(GameRule rule) {
        GameLogic logic = this.logic.get();
        return logic.getRules().test(rule);
    }

    @Override
    public ServerWorld getWorld() {
        return this.bubble.asWorld();
    }

    @Override
    public ConfiguredGame<?> getGameConfig() {
        return this.gameConfig;
    }

    @Override
    public ConfiguredGame<?> getSourceGameConfig() {
        return this.sourceGameConfig;
    }

    @Override
    public GameLifecycle getLifecycle() {
        return this.lifecycle;
    }

    @Override
    public GameStatisticBundle getStatistics(String namespace) {
        return this.statistics.computeIfAbsent(namespace, __ -> new GameStatisticBundle());
    }

    @Override
    public void visitAllStatistics(BiConsumer<String, GameStatisticBundle> consumer) {
        for (Map.Entry<String, GameStatisticBundle> entry : this.statistics.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                consumer.accept(entry.getKey(), entry.getValue());
            }
        }
    }
}
