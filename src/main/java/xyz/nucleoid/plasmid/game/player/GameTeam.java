package xyz.nucleoid.plasmid.game.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.FireworkItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import xyz.nucleoid.codecs.MoreCodecs;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;
import xyz.nucleoid.plasmid.util.ItemUtil;

public final class GameTeam {
    public static final Codec<GameTeam> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                Codec.STRING.fieldOf("key").forGetter(GameTeam::getKey),
                Codec.STRING.fieldOf("display").forGetter(GameTeam::getDisplay),
                MoreCodecs.DYE_COLOR.fieldOf("color").forGetter(GameTeam::getDye)
        ).apply(instance, GameTeam::new);
    });

    private final String display;
    private final String key;
    private final DyeColor dye;
    private final Formatting formatting;

    public GameTeam(String key, String display, DyeColor dye) {
        this.display = display;
        this.key = key;
        this.dye = dye;
        this.formatting = formatByDye(dye);
    }

    public String getDisplay() {
        return this.display;
    }

    public String getKey() {
        return this.key;
    }

    public DyeColor getDye() {
        return this.dye;
    }

    public Formatting getFormatting() {
        return this.formatting;
    }

    public ItemStack createFirework(int flight, FireworkItem.Type type) {
        return ItemUtil.createFirework(this.getFireworkColor(), flight, type);
    }

    public ItemStack dye(ItemStack stack) {
        return ItemStackBuilder.of(stack)
                .setColor(this.getColor())
                .build();
    }

    public int getColor() {
        float[] components = this.dye.getColorComponents();
        int red = MathHelper.floor(components[0] * 255.0F) & 0xFF;
        int green = MathHelper.floor(components[1] * 255.0F) & 0xFF;
        int blue = MathHelper.floor(components[2] * 255.0F) & 0xFF;
        return (red << 16) | (green << 8) | blue;
    }

    public int getFireworkColor() {
        return this.dye.getFireworkColor();
    }

    @Override
    public int hashCode() {
        return this.key.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;

        if (obj instanceof GameTeam) {
            return ((GameTeam) obj).key.equals(this.key);
        }

        return false;
    }

    private static Formatting formatByDye(DyeColor dye) {
        switch (dye) {
            case WHITE: return Formatting.WHITE;
            case ORANGE: return Formatting.GOLD;
            case MAGENTA: return Formatting.LIGHT_PURPLE;
            case LIGHT_BLUE: return Formatting.AQUA;
            case YELLOW: return Formatting.YELLOW;
            case LIME: return Formatting.GREEN;
            case PINK: return Formatting.LIGHT_PURPLE;
            case GRAY: return Formatting.DARK_GRAY;
            case LIGHT_GRAY: return Formatting.GRAY;
            case CYAN: return Formatting.DARK_AQUA;
            case PURPLE: return Formatting.DARK_PURPLE;
            case BLUE: return Formatting.BLUE;
            case BROWN: return Formatting.DARK_RED;
            case GREEN: return Formatting.DARK_GREEN;
            case RED: return Formatting.RED;
            case BLACK: return Formatting.BLACK;
            default: return Formatting.RESET;
        }
    }
}
