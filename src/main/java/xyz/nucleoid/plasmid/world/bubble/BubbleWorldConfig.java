package xyz.nucleoid.plasmid.world.bubble;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.gen.chunk.ChunkGenerator;

import javax.annotation.Nullable;

public final class BubbleWorldConfig {
    private ChunkGenerator generator = null;
    private GameMode defaultGameMode = GameMode.ADVENTURE;
    private BubbleWorldSpawner spawner = BubbleWorldSpawner.at(new Vec3d(0.0, 64.0, 0.0));

    public BubbleWorldConfig setGenerator(ChunkGenerator generator) {
        this.generator = generator;
        return this;
    }

    public BubbleWorldConfig setDefaultGameMode(GameMode gameMode) {
        this.defaultGameMode = gameMode;
        return this;
    }

    public BubbleWorldConfig setSpawnAt(Vec3d spawnPos) {
        return this.setSpawner(BubbleWorldSpawner.at(spawnPos));
    }

    public BubbleWorldConfig setSpawner(BubbleWorldSpawner spawner) {
        this.spawner = spawner;
        return this;
    }

    @Nullable
    public ChunkGenerator getGenerator() {
        return this.generator;
    }

    public GameMode getDefaultGameMode() {
        return this.defaultGameMode;
    }

    public BubbleWorldSpawner getSpawner() {
        return this.spawner;
    }
}