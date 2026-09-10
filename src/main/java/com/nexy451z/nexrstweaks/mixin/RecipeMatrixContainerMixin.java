package com.nexy451z.nexrstweaks.mixin;

import com.nexy451z.nexrstweaks.core.ItemMatchHelper;
import com.refinedmods.refinedstorage.api.storage.root.RootStorage;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Refined Storageの実転送（RecipeMatrixContainer.doTransferRecipe）でも
 * 耐久値系コンポーネント差異を緩和する。
 *
 * JEIの判定（+ボタン）だけ緩和しても、実転送は完全一致の RootStorage.extract と
 * プレイヤーインベントリの完全一致抽出のままのため、耐久減ツールが転送されない。
 * ここでは network 抽出にフォールバックを追加し、抽出に成功した実際の候補を
 * マトリクススロットに配置させる（新品ツールが空から出現しない）。
 * 対象クラスが存在しない/変更された場合は required:false により静かにスキップされる。
 */
@Mixin(targets = "com.refinedmods.refinedstorage.common.support.RecipeMatrixContainer", remap = false)
public abstract class RecipeMatrixContainerMixin {

    /** 直前のredirectでfuzzy抽出に成功した実際のリソース（setItemに反映するため） */
    @Unique
    private static ItemResource nex$fuzzyExtractedResource;

    @Redirect(
            method = "doTransferRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/api/storage/root/RootStorage;extract(Lcom/refinedmods/refinedstorage/api/resource/ResourceKey;JLcom/refinedmods/refinedstorage/api/core/Action;Lcom/refinedmods/refinedstorage/api/storage/Actor;)J"
            ),
            remap = false
    )
    private long nex$fuzzyExtract(RootStorage storage, com.refinedmods.refinedstorage.api.resource.ResourceKey key, long amount,
                                  com.refinedmods.refinedstorage.api.core.Action action, com.refinedmods.refinedstorage.api.storage.Actor actor) {
        nex$fuzzyExtractedResource = null;
        long result = storage.extract(key, amount, action, actor);
        if (result >= 1L) {
            return result;
        }
        try {
            if (key instanceof ItemResource itemKey) {
                ItemStack needed = itemKey.toItemStack();
                if (needed != null && !needed.isEmpty() && ItemMatchHelper.isToolOrDamageable(needed)) {
                    // 完全一致のItemStack生成を避けるため、まずItem単位でフィルタする
                    // (getAll()はCollection<ResourceAmount>)
                    for (com.refinedmods.refinedstorage.api.resource.ResourceAmount entry : java.util.List.copyOf(storage.getAll())) {
                        if (entry == null || !(entry.resource() instanceof ItemResource candidateItem)) continue;
                        if (candidateItem.item() != itemKey.item()) continue;
                        ItemStack held = candidateItem.toItemStack();
                        if (held == null || held.isEmpty()) continue;
                        if (!ItemStack.isSameItem(held, needed)) continue;
                        // 耐久値系アイテム同士のみ緩和（厳格コンポーネントは触らない）
                        if (!ItemMatchHelper.isToolOrDamageable(held)) continue;
                        long fuzzy = storage.extract(candidateItem, amount, action, actor);
                        if (fuzzy >= 1L) {
                            nex$fuzzyExtractedResource = candidateItem;
                            return fuzzy;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    @Redirect(
            method = "doTransferRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/common/support/resource/ItemResource;toItemStack()Lnet/minecraft/world/item/ItemStack;"
            ),
            remap = false
    )
    private ItemStack nex$fuzzySetItem(ItemResource possibility) {
        ItemResource fuzzy = nex$fuzzyExtractedResource;
        nex$fuzzyExtractedResource = null;
        return fuzzy != null ? fuzzy.toItemStack() : possibility.toItemStack();
    }
}
