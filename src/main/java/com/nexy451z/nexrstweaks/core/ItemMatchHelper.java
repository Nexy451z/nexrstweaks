package com.nexy451z.nexrstweaks.core;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * アイテムのマッチング判定ユーティリティ（NexRSTweaksでは耐久値系の緩和判定のみ使用）。
 *
 * ツール・防具・武器等の消耗品は、耐久値（DataComponents.DAMAGE）やエンチャント・名前の違いを
 * 無視し、ベースアイテム単位で一致判定を行う。
 * 一方でポーション（効果違い）、エンチャント本（エンチャント違い）、怪しいシチュー等の
 * 機能的に厳格なコンポーネントを持つアイテムは、ベースアイテム一致でも緩和しない。
 */
public final class ItemMatchHelper {
    private ItemMatchHelper() {
    }

    /**
     * ツールや耐久値を持つアイテム（または消耗可能なアイテム）か判定する。
     * 厳格コンポーネント（ポーション・エンチャント本・怪しいシチュー等）を持つ場合は緩和対象から除外する。
     * 26.1以降は武器/防具/ツールがデータ駆動化されTieredItem/DiggerItem等の型が消滅したため、
     * 「ダメージ値を持つか」で統一的に判定する。
     */
    public static boolean isToolOrDamageable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            if (hasStrictFunctionalComponents(stack)) {
                return false;
            }
            if (stack.isDamageableItem() || stack.getMaxDamage() > 0) {
                return true;
            }
            if (stack.has(DataComponents.DAMAGE) || stack.has(DataComponents.MAX_DAMAGE)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * ポーションやエンチャント本など、クラフトにおいてコンポーネントが必須の意味を持つか確認する。
     */
    private static boolean hasStrictFunctionalComponents(ItemStack stack) {
        try {
            if (stack.has(DataComponents.POTION_CONTENTS)) return true;
            if (stack.has(DataComponents.STORED_ENCHANTMENTS)) return true;
            if (stack.has(DataComponents.SUSPICIOUS_STEW_EFFECTS)) return true;
            if (stack.has(DataComponents.OMINOUS_BOTTLE_AMPLIFIER)) return true;
            if (stack.has(DataComponents.INSTRUMENT)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
