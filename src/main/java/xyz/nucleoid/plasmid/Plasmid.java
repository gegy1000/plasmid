package xyz.nucleoid.plasmid;

import com.google.common.reflect.Reflection;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.*;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.nucleoid.plasmid.command.*;
import xyz.nucleoid.plasmid.event.GameEvents;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameType;
import xyz.nucleoid.plasmid.game.ManagedGameSpace;
import xyz.nucleoid.plasmid.game.channel.ConfiguredChannelSystem;
import xyz.nucleoid.plasmid.game.channel.GameChannelConfig;
import xyz.nucleoid.plasmid.game.channel.GameChannelInterface;
import xyz.nucleoid.plasmid.game.channel.menu.MenuChannelConfig;
import xyz.nucleoid.plasmid.game.channel.on_demand.OnDemandChannelConfig;
import xyz.nucleoid.plasmid.game.composite.RandomGame;
import xyz.nucleoid.plasmid.game.composite.RandomGameConfig;
import xyz.nucleoid.plasmid.game.config.GameConfigs;
import xyz.nucleoid.plasmid.game.event.*;
import xyz.nucleoid.plasmid.game.stats.GameStatisticBundle;
import xyz.nucleoid.plasmid.game.world.generator.VoidChunkGenerator;
import xyz.nucleoid.plasmid.item.PlasmidItems;
import xyz.nucleoid.plasmid.map.template.MapTemplateSerializer;
import xyz.nucleoid.plasmid.map.workspace.MapWorkspaceManager;
import xyz.nucleoid.plasmid.test.TestGame;

import java.util.Map;

public final class Plasmid implements ModInitializer {
    public static final String ID = "plasmid";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    @Override
    public void onInitialize() {
        Reflection.initialize(PlasmidItems.class);

        Registry.register(Registry.CHUNK_GENERATOR, new Identifier(ID, "void"), VoidChunkGenerator.CODEC);

        GameConfigs.register();
        ConfiguredChannelSystem.register();

        MapTemplateSerializer.INSTANCE.register();

        GameChannelConfig.register(new Identifier(ID, "on_demand"), OnDemandChannelConfig.CODEC);
        GameChannelConfig.register(new Identifier(ID, "menu"), MenuChannelConfig.CODEC);

        GameType.register(new Identifier(Plasmid.ID, "test"), TestGame::open, Codec.unit(Unit.INSTANCE));
        GameType.register(new Identifier(Plasmid.ID, "random"), RandomGame::open, RandomGameConfig.CODEC);

        this.registerCallbacks();
    }

    private void registerCallbacks() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            MapManageCommand.register(dispatcher);
            MapMetadataCommand.register(dispatcher);
            GameCommand.register(dispatcher);
            GameChannelCommand.register(dispatcher);
            PartyCommand.register(dispatcher);
            ChatCommand.register(dispatcher);
            ShoutCommand.register(dispatcher);
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player instanceof ServerPlayerEntity && entity instanceof GameChannelInterface && hand == Hand.MAIN_HAND) {
                if (((GameChannelInterface) entity).interactWithChannel((ServerPlayerEntity) player)) {
                    return ActionResult.SUCCESS;
                }
            }

            return ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity) {
                ManagedGameSpace gameSpace = ManagedGameSpace.forWorld(world);
                if (gameSpace != null && gameSpace.containsPlayer((ServerPlayerEntity) player)) {
                    try {
                        UseItemListener invoker = gameSpace.invoker(UseItemListener.EVENT);
                        return invoker.onUseItem((ServerPlayerEntity) player, hand);
                    } catch (Throwable t) {
                        LOGGER.error("An unexpected exception occurred while dispatching use item event", t);
                        gameSpace.reportError(t, "Use item");
                    }
                }
            }

            return TypedActionResult.pass(ItemStack.EMPTY);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity) {
                ManagedGameSpace gameSpace = ManagedGameSpace.forWorld(world);
                if (gameSpace != null && gameSpace.containsPlayer((ServerPlayerEntity) player)) {
                    try {
                        UseBlockListener invoker = gameSpace.invoker(UseBlockListener.EVENT);
                        return invoker.onUseBlock((ServerPlayerEntity) player, hand, hitResult);
                    } catch (Throwable t) {
                        LOGGER.error("An unexpected exception occurred while dispatching use block event", t);
                        gameSpace.reportError(t, "Use block");
                    }
                }
            }

            return ActionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity) {
                ManagedGameSpace gameSpace = ManagedGameSpace.forWorld(world);
                if (gameSpace != null && gameSpace.containsPlayer((ServerPlayerEntity) player)) {
                    try {
                        BreakBlockListener invoker = gameSpace.invoker(BreakBlockListener.EVENT);
                        return invoker.onBreak((ServerPlayerEntity) player, pos) != ActionResult.FAIL;
                    } catch (Throwable t) {
                        LOGGER.error("An unexpected exception occurred while dispatching block break event", t);
                        gameSpace.reportError(t, "Break block");
                    }
                }
            }
            return true;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity) {
                ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

                ManagedGameSpace gameSpace = ManagedGameSpace.forWorld(world);
                if (gameSpace != null && gameSpace.containsPlayer(serverPlayer)) {
                    try {
                        AttackEntityListener invoker = gameSpace.invoker(AttackEntityListener.EVENT);
                        return invoker.onAttackEntity(serverPlayer, hand, entity, hitResult);
                    } catch (Throwable t) {
                        LOGGER.error("An unexpected exception occurred while dispatching attack entity event", t);
                        gameSpace.reportError(t, "Attack entity");
                    }
                }
            }

            return ActionResult.PASS;
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            ManagedGameSpace game = ManagedGameSpace.forWorld(world);
            if (game != null) {
                try {
                    game.invoker(GameTickListener.EVENT).onTick();
                } catch (Throwable t) {
                    LOGGER.error("An unexpected exception occurred while ticking the game", t);
                    game.reportError(t, "Ticking game");

                    game.closeWithError("An unexpected error occurred while ticking the game");
                }
            }
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            MapWorkspaceManager.get(server).tick();
        });

        ServerLifecycleEvents.SERVER_STARTING.register(ConfiguredChannelSystem.INSTANCE::setup);

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (ManagedGameSpace gameSpace : ManagedGameSpace.getOpen()) {
                gameSpace.close(GameCloseReason.CANCELED);
            }
        });

        ServerWorldEvents.UNLOAD.register((server, world) -> {
            ManagedGameSpace gameSpace = ManagedGameSpace.forWorld(world);
            if (gameSpace != null) {
                gameSpace.close(GameCloseReason.CANCELED);
            }
        });

        // For games to debug their statistic collection without needing to setup a backend
        if (Boolean.getBoolean("plasmid.debug_statistics")) {
            GameEvents.CLOSING.register((gameSpace, reason) -> {
                gameSpace.visitAllStatistics((name, bundle) -> {
                    LOGGER.info(bundle.encodeBundle());
                });
            });
        }
    }
}
