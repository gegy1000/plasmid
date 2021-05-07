package xyz.nucleoid.plasmid.game.channel.oneshot;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.event.GameEvents;
import xyz.nucleoid.plasmid.game.*;
import xyz.nucleoid.plasmid.game.channel.GameChannel;
import xyz.nucleoid.plasmid.game.channel.GameChannelSystem;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class OneshotChannelSystem implements GameChannelSystem {
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz012345679";

    private final MinecraftServer server;
    private final Map<Identifier, GameChannel> channels = new Object2ObjectOpenHashMap<>();

    public OneshotChannelSystem(MinecraftServer server) {
        this.server = server;
    }

    public CompletableFuture<GameChannel> open(ConfiguredGame<?> game) {
        GameEvents.ONE_SHOT_OPENING.invoker().onOneShotGameOpening(game);

        return game.open(this.server).thenApplyAsync(gameSpace -> {
            GameChannel channel = this.createChannel(gameSpace);
            this.channels.put(channel.getId(), channel);

            return channel;
        }, this.server);
    }

    private GameChannel createChannel(ManagedGameSpace gameSpace) {
        Identifier id = this.createChannelIdFor(gameSpace.getGameConfig().getType());

        gameSpace.getLifecycle().addListeners(new GameLifecycle.Listeners() {
            @Override
            public void onClosed(GameSpace gameSpace, List<ServerPlayerEntity> players, GameCloseReason reason) {
                OneshotChannelSystem.this.closeChannel(id);
            }
        });

        return new GameChannel(this.server, id, (server, channelId, members) -> new OneshotChannelBackend(gameSpace, members));
    }

    private Identifier createChannelIdFor(GameType<?> gameType) {
        Identifier gameId = gameType.getIdentifier();
        return new Identifier(gameId.getNamespace(), gameId.getPath() + "_" + RandomStringUtils.random(6, ALPHABET));
    }

    private void closeChannel(Identifier id) {
        GameChannel channel = this.channels.remove(id);
        if (channel != null) {
            channel.invalidate();
        }
    }

    @Override
    public Set<Identifier> keySet() {
        return this.channels.keySet();
    }

    @Override
    public Collection<GameChannel> getChannels() {
        return this.channels.values();
    }

    @Override
    @Nullable
    public GameChannel byId(Identifier id) {
        return this.channels.get(id);
    }
}
