package xyz.nucleoid.plasmid.game.channel;

import com.mojang.serialization.Codec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.game.config.CustomValuesConfig;
import xyz.nucleoid.plasmid.registry.TinyRegistry;

import java.util.function.Function;

public interface GameChannelConfig {
    TinyRegistry<Codec<? extends GameChannelConfig>> REGISTRY = TinyRegistry.newStable();
    Codec<GameChannelConfig> CODEC = REGISTRY.dispatchStable(GameChannelConfig::getCodec, Function.identity());

    static void register(Identifier key, Codec<? extends GameChannelConfig> codec) {
        REGISTRY.register(key, codec);
    }

    GameChannelBackend createBackend(MinecraftServer server, Identifier id, GameChannelMembers members);

    CustomValuesConfig getCustom();

    Codec<? extends GameChannelConfig> getCodec();
}
