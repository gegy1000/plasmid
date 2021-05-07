package xyz.nucleoid.plasmid.game.channel;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.game.config.CustomValuesConfig;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.party.PartyManager;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class GameChannel {
    private final Identifier id;
    private final GameChannelMembers members;
    private final GameChannelBackend backend;
    private CustomValuesConfig custom = CustomValuesConfig.empty();

    private final Set<GameChannelInterface> interfaces = new ObjectOpenHashSet<>();

    public GameChannel(MinecraftServer server, Identifier id, GameChannelBackend.Factory<?> backendFactory) {
        this.id = id;
        this.members = new GameChannelMembers(server, this);
        this.backend = backendFactory.create(server, id, this.members);
    }

    void setCustom(CustomValuesConfig custom) {
        this.custom = custom;
    }

    public Identifier getId() {
        return this.id;
    }

    public CustomValuesConfig getCustom() {
        return this.custom;
    }

    public void requestJoin(ServerPlayerEntity player) {
        PartyManager partyManager = PartyManager.get(player.server);
        Collection<ServerPlayerEntity> players = partyManager.getPartyMembers(player);

        this.requestJoinAll(players).thenAcceptAsync(result -> {
            if (result.isError()) {
                player.sendMessage(result.getError().shallowCopy().formatted(Formatting.RED), false);
            }
        }, player.server);
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<JoinResult> requestJoinAll(Collection<ServerPlayerEntity> players) {
        int i = 0;
        CompletableFuture<JoinResult>[] futures = new CompletableFuture[players.size()];
        for (ServerPlayerEntity player : players) {
            futures[i++] = this.backend.requestJoin(player);
        }

        return CompletableFuture.allOf(futures).handle((v, throwable) -> {
            if (throwable != null) {
                return JoinResult.err(this.getFeedbackForException(throwable));
            }

            for (CompletableFuture<JoinResult> future : futures) {
                JoinResult result = future.join();
                if (result.isError()) {
                    return result;
                }
            }

            return JoinResult.ok();
        });
    }

    private MutableText getFeedbackForException(Throwable throwable) {
        GameOpenException gameOpenException = GameOpenException.unwrap(throwable);
        if (gameOpenException != null) {
            return gameOpenException.getReason().shallowCopy();
        } else {
            return new TranslatableText("text.plasmid.game.join.error");
        }
    }

    public boolean addInterface(GameChannelInterface itf) {
        if (itf.getChannel() == null && this.interfaces.add(itf)) {
            itf.setChannel(this);
            itf.setDisplay(this.buildDisplay());
            return true;
        }
        return false;
    }

    public boolean removeInterface(GameChannelInterface itf) {
        if (this.interfaces.remove(itf)) {
            itf.invalidateChannel();
            return true;
        }
        return false;
    }

    public void invalidate() {
        for (GameChannelInterface itf : this.interfaces) {
            itf.invalidateChannel();
        }
        this.interfaces.clear();
    }

    void updateDisplay() {
        Text[] display = this.buildDisplay();
        for (GameChannelInterface itf : this.interfaces) {
            itf.setDisplay(display);
        }
    }

    private Text[] buildDisplay() {
        return new Text[] {
                this.backend.getName(),
                new LiteralText(this.members.size() + " players")
        };
    }

    public boolean containsPlayer(ServerPlayerEntity player) {
        return this.members.containsPlayer(player);
    }

    public int getPlayerCount() {
        return this.members.size();
    }

    public Text getName() {
        return this.backend.getName();
    }

    public Text createJoinLink() {
        ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/game join " + this.id);
        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TranslatableText("text.plasmid.channel.join_link_hover", this.getName()));
        Style style = Style.EMPTY
                .withFormatting(Formatting.UNDERLINE)
                .withColor(Formatting.BLUE)
                .withClickEvent(click)
                .withHoverEvent(hover);

        return new TranslatableText("text.plasmid.game.open.join").setStyle(style);
    }
}
