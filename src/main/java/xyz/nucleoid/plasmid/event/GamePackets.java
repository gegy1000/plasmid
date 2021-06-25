package xyz.nucleoid.plasmid.event;

import java.util.function.Consumer;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import xyz.nucleoid.plasmid.Plasmid;
import xyz.nucleoid.plasmid.game.ConfiguredGame;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.GameType;

/**
 * A helper class for creating {@linkplain net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket custom payload packets} to send to clients.
 */
public final class GamePackets {
    private GamePackets() { }

    public static CustomPayloadS2CPacket playerAdd(GameSpace gameSpace, ServerPlayerEntity player) {
        return createPacket("player_add", buf -> writeGameAndPlayerInfo(buf, gameSpace, player));
    }

    public static CustomPayloadS2CPacket playerRemove(GameSpace gameSpace, ServerPlayerEntity player) {
        return createPacket("player_remove", buf -> writeGameAndPlayerInfo(buf, gameSpace, player));
    }

    public static CustomPayloadS2CPacket gameClose(GameSpace gameSpace, GameCloseReason reason) {
        return createPacket("game_close", buf -> {
            writeGameInfo(buf, gameSpace);
            buf.writeString(reason == GameCloseReason.FINISHED ? "finished" : "canceled");
        });
    }

    private static void writeGameAndPlayerInfo(PacketByteBuf buf, GameSpace gameSpace, ServerPlayerEntity player) {
        writeGameInfo(buf, gameSpace);
        writePlayerInfo(buf, gameSpace, player);
    }

    private static void writeGameInfo(PacketByteBuf buf, GameSpace gameSpace) {
        ConfiguredGame<?> game = gameSpace.getGameConfig();
        GameType<?> gameType = game.getType();

        // Game type ID and name
        buf.writeIdentifier(gameType.getIdentifier());
        buf.writeText(gameType.getName());

        // Game ID and name
        buf.writeIdentifier(game.getSource());
        buf.writeText(game.getNameText());
    }

    private static void writePlayerInfo(PacketByteBuf buf, GameSpace gameSpace, ServerPlayerEntity player) {
        buf.writeInt(gameSpace.getPlayerCount());
        buf.writeUuid(player.getUuid());
    }

    private static CustomPayloadS2CPacket createPacket(String type, Consumer<PacketByteBuf> writer) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        writer.accept(buf);

        return new CustomPayloadS2CPacket(createChannel(type), buf);
    }

    private static Identifier createChannel(String type) {
        return new Identifier(Plasmid.ID, "game/" + type);
    }
}
