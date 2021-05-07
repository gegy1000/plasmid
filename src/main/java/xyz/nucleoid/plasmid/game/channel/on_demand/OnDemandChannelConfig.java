package xyz.nucleoid.plasmid.game.channel.on_demand;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.game.channel.GameChannelBackend;
import xyz.nucleoid.plasmid.game.channel.GameChannelConfig;
import xyz.nucleoid.plasmid.game.channel.GameChannelMembers;
import xyz.nucleoid.plasmid.game.config.CustomValuesConfig;

public final class OnDemandChannelConfig implements GameChannelConfig {
    public static final Codec<OnDemandChannelConfig> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Identifier.CODEC.fieldOf("game").forGetter(channel -> channel.gameId),
                Codec.BOOL.optionalFieldOf("continuous", false).forGetter(channel -> channel.continuous),
                CustomValuesConfig.CODEC.optionalFieldOf("custom", CustomValuesConfig.empty()).forGetter(config -> config.custom)
        ).apply(instance, OnDemandChannelConfig::new);
    });

    private final Identifier gameId;
    private final boolean continuous;
    private final CustomValuesConfig custom;

    public OnDemandChannelConfig(Identifier gameId, boolean continuous, CustomValuesConfig custom) {
        this.gameId = gameId;
        this.continuous = continuous;
        this.custom = custom;
    }

    @Override
    public GameChannelBackend createBackend(MinecraftServer server, Identifier id, GameChannelMembers members) {
        if (this.continuous) {
            return new ContinuousOnDemandChannelBackend(this.gameId, members);
        } else {
            return new OnDemandChannelBackend(this.gameId, members);
        }
    }

    @Override
    public CustomValuesConfig getCustom() {
        return this.custom;
    }

    @Override
    public Codec<? extends GameChannelConfig> getCodec() {
        return CODEC;
    }
}
