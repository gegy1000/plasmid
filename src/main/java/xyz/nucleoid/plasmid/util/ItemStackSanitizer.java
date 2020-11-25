package xyz.nucleoid.plasmid.util;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

public final class ItemStackSanitizer {
    private static final Set<String> WHITELISTED_KEYS = new HashSet<>();

    public static boolean isWhitelistedKey(String key) {
        return WHITELISTED_KEYS.contains(key);
    }

    public static void addWhitelistedKey(String key) {
        WHITELISTED_KEYS.add(key);
    }

    /**
     * Makes a sanitized copy of an item stack.
     * 
     * @param stack the stack to sanitize
     */
    public static ItemStack sanitize(ItemStack stack) {
        ItemStack sanitizedStack = stack.copy();

        CompoundTag tag = sanitizedStack.getTag();
        if (tag != null) {
            for (String key : tag.getKeys()) {
                if (!isWhitelistedKey(key)) {
                    tag.remove(key);
                }
            }
        }

        return sanitizedStack;
    }
    
    static {
        // General
        addWhitelistedKey("Damage");
        addWhitelistedKey("Unbreakable");

        // Specific to certain items
        addWhitelistedKey("BucketVariantTag"); // Tropical fish buckets
        addWhitelistedKey("color"); // Leather armor
        addWhitelistedKey("SkullOwner"); // Player heads
    }
}
