package xyz.nucleoid.plasmid.game.channel;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.Plasmid;
import xyz.nucleoid.plasmid.registry.TinyRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public final class ConfiguredChannelSystem implements GameChannelSystem {
    public static final ConfiguredChannelSystem INSTANCE = new ConfiguredChannelSystem();

    private static final String PATH = "game_channels";

    private final TinyRegistry<GameChannel> channels = TinyRegistry.newStable();
    private Map<Identifier, GameChannelConfig> channelQueue;

    private MinecraftServer server;

    private ConfiguredChannelSystem() {
    }

    public static void register() {
        ResourceManagerHelper serverData = ResourceManagerHelper.get(ResourceType.SERVER_DATA);
        serverData.registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return new Identifier(Plasmid.ID, "game_channels");
            }

            @Override
            public void apply(ResourceManager manager) {
                INSTANCE.reload(manager);
            }
        });
    }

    public void setup(MinecraftServer server) {
        this.server = server;

        Map<Identifier, GameChannelConfig> queue = this.channelQueue;
        if (queue != null) {
            this.channelQueue = null;
            this.loadChannelsFrom(server, queue);
        }
    }

    private void reload(ResourceManager manager) {
        this.channels.clear();

        Map<Identifier, GameChannelConfig> configs = this.loadConfigs(manager);
        if (this.server != null) {
            this.loadChannelsFrom(this.server, configs);
        } else {
            this.channelQueue = configs;
        }
    }

    private void loadChannelsFrom(MinecraftServer server, Map<Identifier, GameChannelConfig> queue) {
        for (Map.Entry<Identifier, GameChannelConfig> entry : queue.entrySet()) {
            Identifier identifier = entry.getKey();
            GameChannelConfig config = entry.getValue();

            GameChannel channel = new GameChannel(server, identifier, config::createBackend);
            channel.setCustom(config.getCustom());

            this.channels.register(identifier, channel);
        }
    }

    private Map<Identifier, GameChannelConfig> loadConfigs(ResourceManager manager) {
        Map<Identifier, GameChannelConfig> configs = new Object2ObjectOpenHashMap<>();

        Collection<Identifier> resources = manager.findResources(PATH, path -> path.endsWith(".json"));

        for (Identifier path : resources) {
            try {
                Resource resource = manager.getResource(path);
                try (Reader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
                    JsonElement json = new JsonParser().parse(reader);

                    Identifier identifier = identifierFromPath(path);

                    DataResult<GameChannelConfig> result = GameChannelConfig.CODEC.decode(JsonOps.INSTANCE, json).map(Pair::getFirst);

                    result.result().ifPresent(config -> {
                        configs.put(identifier, config);
                    });

                    result.error().ifPresent(error -> {
                        Plasmid.LOGGER.error("Failed to decode game channel at {}: {}", path, error.toString());
                    });
                }
            } catch (IOException e) {
                Plasmid.LOGGER.error("Failed to read game channel at {}", path, e);
            }
        }

        return configs;
    }

    private static Identifier identifierFromPath(Identifier location) {
        String path = location.getPath();
        path = path.substring(PATH.length() + 1, path.length() - ".json".length());
        return new Identifier(location.getNamespace(), path);
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
