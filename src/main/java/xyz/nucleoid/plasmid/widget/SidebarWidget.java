package xyz.nucleoid.plasmid.widget;

import com.google.common.base.Preconditions;
import fr.catcore.server.translations.api.LocalizationTarget;
import fr.catcore.server.translations.api.text.LocalizableText;
import it.unimi.dsi.fastutil.chars.CharArrayList;
import it.unimi.dsi.fastutil.chars.CharList;
import it.unimi.dsi.fastutil.chars.CharOpenHashSet;
import it.unimi.dsi.fastutil.chars.CharSet;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardPlayerUpdateS2CPacket;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import xyz.nucleoid.plasmid.Plasmid;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.player.MutablePlayerSet;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class SidebarWidget implements GameWidget {
    private static final int SIDEBAR_SLOT = 1;
    private static final int ADD_OBJECTIVE = 0;
    private static final int REMOVE_OBJECTIVE = 1;
    private static final int SET_OBJECTIVE_TITLE = 2;

    private static final String OBJECTIVE_NAME = Plasmid.ID + ":sidebar";

    private static final String FORMATTING_CHARS = "abcdefghijklmnopqrstuvwxyz.!?*+-(){}|";
    private static final char[] AVAILABLE_FORMATTING_CODES;

    private static final int MAX_WIDTH = 40;

    static {
        CharSet vanillaFormattingCodes = new CharOpenHashSet();
        for (Formatting formatting : Formatting.values()) {
            vanillaFormattingCodes.add(formatting.toString().charAt(1));
        }

        CharList availableFormattingCodes = new CharArrayList();
        for (int i = 0; i < FORMATTING_CHARS.length(); i++) {
            char code = FORMATTING_CHARS.charAt(i);
            if (!vanillaFormattingCodes.contains(code)) {
                availableFormattingCodes.add(code);
            }
        }

        AVAILABLE_FORMATTING_CODES = availableFormattingCodes.toCharArray();
    }

    private final MutablePlayerSet players;
    private Text title;

    private final Content content = new Content();

    public SidebarWidget(GameSpace gameSpace, Text title) {
        this(gameSpace.getServer(), title);
    }

    public SidebarWidget(MinecraftServer server, Text title) {
        this.players = new MutablePlayerSet(server);
        this.title = title;
    }

    public void setTitle(Text title) {
        this.title = title;

        for (ServerPlayerEntity player : this.players) {
            ScoreboardObjective objective = this.createDummyObjective(player);
            player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(objective, SET_OBJECTIVE_TITLE));
        }
    }

    public void set(Consumer<Content> writer) {
        writer.accept(this.content);
        this.content.flush();
    }

    @Override
    public void addPlayer(ServerPlayerEntity player) {
        if (this.players.add(player)) {
            ScoreboardObjective objective = this.createDummyObjective(player);

            player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(objective, ADD_OBJECTIVE));
            player.networkHandler.sendPacket(new ScoreboardDisplayS2CPacket(SIDEBAR_SLOT, objective));

            this.content.sendTo(player);
        }
    }

    @Override
    public void removePlayer(ServerPlayerEntity player) {
        if (this.players.remove(player)) {
            this.sendRemoveForPlayer(player);
        }
    }

    @Override
    public void close() {
        for (ServerPlayerEntity player : this.players) {
            this.sendRemoveForPlayer(player);
        }
        this.players.clear();
    }

    private void sendRemoveForPlayer(ServerPlayerEntity player) {
        ScoreboardObjective objective = this.createDummyObjective(player);
        player.networkHandler.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(objective, REMOVE_OBJECTIVE));
    }

    private ScoreboardObjective createDummyObjective(ServerPlayerEntity player) {
        return new ScoreboardObjective(
                null, OBJECTIVE_NAME,
                ScoreboardCriterion.DUMMY,
                LocalizableText.asLocalizedFor(this.title, (LocalizationTarget) player),
                ScoreboardCriterion.RenderType.INTEGER
        );
    }

    private static String modifyLine(int i, String line) {
        line = "\u00a7" + AVAILABLE_FORMATTING_CODES[i] + line;
        if (line.length() > MAX_WIDTH) {
            line = line.substring(0, MAX_WIDTH - 1);
        }
        return line;
    }

    public class Content {
        private Lines lines = new Lines();
        private Lines lastLines = new Lines();

        public Content writeLine(String line) {
            return this.writeRawLine(line);
        }

        public Content writeLineText(Text text) {
            return this.writeRawLine(text);
        }

        public Content writeFormattedTranslated(Formatting formatting, String key, Object... args) {
            return this.writeRawLine(new TranslatableText(key, args).formatted(formatting));
        }

        public Content writeFormattedTranslated(Formatting[] formatting, String key, Object... args) {
            return this.writeRawLine(new TranslatableText(key, args).formatted(formatting));
        }

        public Content writeFormattedTranslated(List<Formatting> formatting, String key, Object... args) {
            return this.writeRawLine(new TranslatableText(key, args).formatted(formatting.toArray(new Formatting[0])));
        }

        public Content writeTranslated(String key, Object... args) {
            return this.writeRawLine(new TranslatableText(key, args));
        }

        private Content writeRawLine(Object line) {
            this.lines.push(line);
            return this;
        }

        void flush() {
            MutablePlayerSet players = SidebarWidget.this.players;

            int length = this.lines.length;
            int lastLength = this.lastLines.length;

            // update all lines that have changed, indexed by score
            int maxScore = Math.max(length, lastLength);
            for (int score = 1; score <= maxScore; score++) {
                Object line = this.lines.byScore(score);
                Object lastLine = this.lastLines.byScore(score);
                if (Objects.equals(line, lastLine)) {
                    continue;
                }

                for (ServerPlayerEntity player : players) {
                    if (lastLine != null) {
                        this.sendRemoveLine(player, score);
                    }
                    if (line != null) {
                        this.sendUpdateLine(player, this.getLineForPlayer(line, score, player), score);
                    }
                }
            }

            Lines swap = this.lastLines;
            swap.clear();

            this.lastLines = this.lines;
            this.lines = swap;
        }

        void sendTo(ServerPlayerEntity player) {
            Lines lines = this.lastLines;
            for (int i = 0; i < lines.length; i++) {
                String line = this.getLineForPlayer(lines.byIndex(i), lines.scoreFor(i), player);
                this.sendUpdateLine(player, line, lines.scoreFor(i));
            }
        }

        void sendUpdateLine(ServerPlayerEntity player, String line, int score) {
            player.networkHandler.sendPacket(new ScoreboardPlayerUpdateS2CPacket(
                    ServerScoreboard.UpdateMode.CHANGE, OBJECTIVE_NAME,
                    line, score
            ));
        }

        void sendRemoveLine(ServerPlayerEntity player, int score) {
            String line = this.getLineForPlayer(this.lastLines.byScore(score), score, player);
            player.networkHandler.sendPacket(new ScoreboardPlayerUpdateS2CPacket(
                    ServerScoreboard.UpdateMode.REMOVE, null,
                    line, -1
            ));
        }

        String getLineForPlayer(Object line, int score, ServerPlayerEntity player) {
            String text;
            if (line instanceof String) {
                text = (String) line;
            } else if (line instanceof Text) {
                Text txt = (Text) line;

                StringBuilder message = new StringBuilder();
                Style style = txt.getStyle();
                if (style.getColor() != null && !style.getColor().getName().startsWith("#")) {
                    message.append(Objects.requireNonNull(
                            Formatting.byName(style.getColor().getName()),
                            "formatting"
                    ).toString());
                }
                if (style.isBold()) {
                    message.append(Formatting.BOLD);
                }
                if (style.isItalic()) {
                    message.append(Formatting.ITALIC);
                }
                if (style.isUnderlined()) {
                    message.append(Formatting.UNDERLINE);
                }
                if (style.isStrikethrough()) {
                    message.append(Formatting.STRIKETHROUGH);
                }
                if (style.isObfuscated()) {
                    message.append(Formatting.OBFUSCATED);
                }

                message.append(LocalizableText.asLocalizedFor(txt, (LocalizationTarget) player).getString());
                text = message.toString();
            } else {
                text = line.toString();
            }
            return modifyLine(score, text);
        }
    }

    private static class Lines {
        final Object[] array = new Object[15];
        int length;

        void clear() {
            this.length = 0;
            Arrays.fill(this.array, null);
        }

        void push(Object line) {
            Preconditions.checkNotNull(line, "cannot write null line");
            if (this.length < 15) {
                this.array[this.length++] = line;
            }
        }

        int scoreFor(int index) {
            return this.length - index;
        }

        Object byIndex(int index) {
            if (index < 0 || index >= this.length) {
                return null;
            }
            return this.array[index];
        }

        Object byScore(int score) {
            return this.byIndex(this.length - score);
        }
    }
}
