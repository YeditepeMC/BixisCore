package com.yeditepemc.bixiscore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GUI ve mesaj yardımcıları. Legacy '&' renk kodlarını Adventure component'lerine
 * çevirir ve item oluşturmayı kolaylaştırır. Tüm metinler Türkçe (CLAUDE.md — Dil).
 */
public final class TextUtil {

    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    private TextUtil() {
    }

    /** "1240" → "1.240" (binlik ayraç nokta). */
    public static String num(long value) {
        return String.format(Locale.GERMAN, "%,d", value);
    }

    /** '&' kodlu metni component'e çevirir; item isimlerindeki varsayılan italik kapatılır. */
    public static Component comp(String legacy) {
        return AMP.deserialize(legacy).decoration(TextDecoration.ITALIC, false);
    }

    public static List<Component> comps(List<String> legacyLines) {
        List<Component> out = new ArrayList<>(legacyLines.size());
        for (String line : legacyLines) {
            out.add(comp(line));
        }
        return out;
    }

    /** İsim + lore ile item üretir. */
    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(comp(name));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(comps(lore));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack item(Material material, String name) {
        return item(material, name, List.of());
    }
}
