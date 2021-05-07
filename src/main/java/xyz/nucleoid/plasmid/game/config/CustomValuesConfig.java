package xyz.nucleoid.plasmid.game.config;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;

public final class CustomValuesConfig {
    public static final Codec<CustomValuesConfig> CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.PASSTHROUGH)
            .xmap(CustomValuesConfig::new, config -> config.values);

    private static final CustomValuesConfig EMPTY = new CustomValuesConfig(ImmutableMap.of());

    private final Map<Identifier, Dynamic<?>> values;

    private CustomValuesConfig(Map<Identifier, Dynamic<?>> values) {
        this.values = values;
    }

    public static CustomValuesConfig empty() {
        return EMPTY;
    }

    public <T> Optional<T> get(Codec<T> codec, Identifier key) {
        Dynamic<?> value = this.values.get(key);
        if (value != null) {
            return codec.decode(value).result().map(Pair::getFirst);
        } else {
            return Optional.empty();
        }
    }

    public boolean isEmpty() {
        return this.values.isEmpty();
    }
}
