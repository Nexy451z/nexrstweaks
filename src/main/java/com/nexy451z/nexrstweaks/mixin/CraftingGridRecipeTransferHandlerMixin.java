package com.nexy451z.nexrstweaks.mixin;

import com.nexy451z.nexrstweaks.core.ItemMatchHelper;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceList;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Refined Storage クラフトグリッドの JEI 転送ハンドラ(+ボタン)で、
 * 耐久値が減ったツール(クワ・ツルハシ等)と「レシピの新品スタック」とのコンポーネント差を
 * 無視して引き当てられるように緩和する。
 * 完全一致 remove が失敗した場合に限り、同アイテムで耐久値系の違いを許容して再試行する
 * (ItemMatchHelper の fuzzy 判定と同じ基準)。
 * 対象クラスが存在しない/変更された場合は required:false により静かにスキップされる。
 */
@Mixin(targets = "com.refinedmods.refinedstorage.jei.common.CraftingGridRecipeTransferHandler", remap = false)
public abstract class CraftingGridRecipeTransferHandlerMixin {

    @Redirect(
            method = "toTransferInput",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/refinedmods/refinedstorage/api/resource/list/MutableResourceList;remove(Lcom/refinedmods/refinedstorage/api/resource/ResourceKey;J)Lcom/refinedmods/refinedstorage/api/resource/list/MutableResourceList$OperationResult;"
            ),
            remap = false
    )
    private static MutableResourceList.OperationResult nex$fuzzyRemove(MutableResourceList available, ResourceKey key, long amount) {
        MutableResourceList.OperationResult result = available.remove(key, amount);
        if (result != null) {
            return result;
        }
        try {
            if (key instanceof ItemResource itemKey) {
                ItemStack needed = itemKey.toItemStack();
                if (needed != null && !needed.isEmpty() && ItemMatchHelper.isToolOrDamageable(needed)) {
                    // 呼び出し元 keySet のループ中 mutation を避けるためスナップショットを走査し、
                    // 完全一致の ItemStack が取れない候補は生 Item 単位でフィルタする
                    for (ResourceKey candidate : java.util.List.copyOf(available.getAll())) {
                        if (!(candidate instanceof ItemResource candidateItem)) continue;
                        if (candidateItem.item() != itemKey.item()) continue;
                        ItemStack held = candidateItem.toItemStack();
                        if (held == null || held.isEmpty()) continue;
                        if (!ItemStack.isSameItem(held, needed)) continue;
                        // 耐久値系アイテム同士のみ緩和（ポーション等の厳格コンポーネントは触らない）
                        if (!ItemMatchHelper.isToolOrDamageable(held)) continue;
                        MutableResourceList.OperationResult fuzzy = available.remove(candidate, amount);
                        if (fuzzy != null) {
                            return fuzzy;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
