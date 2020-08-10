package net.gegy1000.plasmid.block;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import net.gegy1000.plasmid.registry.TinyRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Set;

public class CustomBlock {
    private static final TinyRegistry<CustomBlock> REGISTRY = TinyRegistry.newStable();
    private static final Long2ObjectArrayMap<CustomBlock> BLOCK_MAP = new Long2ObjectArrayMap<>();

    private final Identifier id;
    private final Text name;

    private CustomBlock(Identifier identifier, Text name) {
        this.id = identifier;
        this.name = name;
    }

    public Identifier getId() {
        return id;
    }

    public static Set<Identifier> getIds() {
        return REGISTRY.keySet();
    }

    @Nullable
    public static CustomBlock get(Identifier identifier) {
        return REGISTRY.get(identifier);
    }

    public boolean setBlock(BlockPos pos) {
        if (this.name != null && !BLOCK_MAP.containsKey(pos.asLong())) {
            BLOCK_MAP.put(pos.asLong(), this);
            return true;
        }
        return false;
    }

    public boolean setBlock(BlockPos pos, BlockState state, ServerWorld world) {
        if (this.name != null && !BLOCK_MAP.containsKey(pos.asLong())) {
            BLOCK_MAP.put(pos.asLong(), this);
            world.setBlockState(pos, state);
            return true;
        }
        return false;
    }

    @Nullable
    public static CustomBlock matches(BlockPos pos) {
        return BLOCK_MAP.get(pos.asLong());
    }

    public static CustomBlock.Builder builder() {
        return new CustomBlock.Builder();
    }

    public static class Builder {
        private Identifier id;
        private Text name;

        private Builder() {
        }

        public CustomBlock.Builder id(Identifier id) {
            this.id = id;
            return this;
        }

        public CustomBlock.Builder name(Text name) {
            this.name = name;
            return this;
        }

        public CustomBlock register() {
            Preconditions.checkNotNull(this.id, "id not set");
            if (REGISTRY.containsKey(this.id)) {
                throw new IllegalArgumentException(this.id + " already registered");
            }

            CustomBlock block = new CustomBlock(this.id, this.name);

            REGISTRY.register(this.id, block);

            return block;
        }
    }
}
