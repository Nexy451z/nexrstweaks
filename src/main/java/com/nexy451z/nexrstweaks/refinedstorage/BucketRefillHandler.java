package com.nexy451z.nexrstweaks.refinedstorage;

import com.nexy451z.nexrstweaks.NexRSTweaks;
import com.refinedmods.refinedstorage.common.grid.AbstractCraftingGridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.CraftingGrid;
import com.refinedmods.refinedstorage.common.api.grid.Grid;
import com.refinedmods.refinedstorage.common.support.RecipeMatrixContainer;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Refined Storage クラフトグリッド上のバケツ系アイテム自動補填。
 *
 * 「スロットに素材（例: 水バケツ）→クラフト→スロットに空容器（バケツ）」の遷移を検知して、
 * ストレージに素材があれば抽出し、空容器をストレージに戻した上でスロットを素材で満たす。
 * ストレージに素材がなければ何もしない（空容器は通常通り残る）。
 * プレイヤーが最初から置いた空容器は遷移検知により触られない。
 */
@EventBusSubscriber(modid = NexRSTweaks.MODID)
public class BucketRefillHandler {

    /** プレイヤーごとのマトリクス前回状態（補填トリガー判定用）: UUID -> スロット9個分 */
    private static final Map<UUID, ItemStack[]> bucketState = new ConcurrentHashMap<>();
    /** 空容器 -> その容器を生成しうる元アイテム群（遅延構築） */
    private static volatile Map<Item, List<Item>> containerSourceMap = null;

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            bucketState.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 2 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof AbstractCraftingGridContainerMenu menu)) {
                bucketState.remove(player.getUUID());
                continue;
            }
            refillBucketLikeSlots(player, menu);
        }
    }

    private static Map<Item, List<Item>> containerSources() {
        Map<Item, List<Item>> map = containerSourceMap;
        if (map != null) return map;
        synchronized (BucketRefillHandler.class) {
            if (containerSourceMap == null) {
                Map<Item, List<Item>> built = new HashMap<>();
                for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                    try {
                        if (!item.hasCraftingRemainingItem()) continue;
                        Item remaining = item.getCraftingRemainingItem();
                        if (remaining != null) {
                            built.computeIfAbsent(remaining, k -> new ArrayList<>()).add(item);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                containerSourceMap = Map.copyOf(built);
            }
            return containerSourceMap;
        }
    }

    private static void refillBucketLikeSlots(ServerPlayer player, AbstractCraftingGridContainerMenu menu) {
        CraftingGrid grid = RefinedStorageAccess.getCraftingGrid(menu);
        if (grid == null) {
            bucketState.remove(player.getUUID());
            return;
        }
        try {
            RecipeMatrixContainer matrix = grid.getCraftingMatrix();
            if (matrix == null) return;
            com.refinedmods.refinedstorage.api.storage.Storage storage =
                    RefinedStorageAccess.getGridStorage(player);
            if (storage == null) return;

            int size = Math.min(9, matrix.getContainerSize());
            ItemStack[] prev = bucketState.computeIfAbsent(player.getUUID(), k -> new ItemStack[9]);
            com.refinedmods.refinedstorage.common.api.storage.PlayerActor actor =
                    new com.refinedmods.refinedstorage.common.api.storage.PlayerActor(player);

            for (int i = 0; i < size; i++) {
                ItemStack cur = matrix.getItem(i).copy();
                ItemStack prevStack = prev[i];
                prev[i] = cur.copy();
                if (cur.isEmpty() || cur.getCount() != 1 || prevStack == null || prevStack.isEmpty()) continue;
                if (!ItemStack.isSameItem(cur, prevStack)) continue;

                // 前回が「素材」で今回が「その残余」のときのみ補填する（プレイヤーが最初から置いた空容器は触らない）
                Item expectedEmpty = prevStack.getItem().getCraftingRemainingItem();
                if (expectedEmpty == null || expectedEmpty != cur.getItem()) continue;

                List<Item> sources = containerSources().get(cur.getItem());
                if (sources == null || sources.isEmpty()) continue;
                for (Item source : sources) {
                    if (tryRefillFromStorage(storage, actor, matrix, i, cur, source)) {
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean tryRefillFromStorage(
            com.refinedmods.refinedstorage.api.storage.Storage storage,
            com.refinedmods.refinedstorage.common.api.storage.PlayerActor actor,
            RecipeMatrixContainer matrix,
            int slot,
            ItemStack emptyContainer,
            Item source
    ) {
        try {
            ItemResource full = ItemResource.ofItemStack(new ItemStack(source, 1));
            ItemResource empty = ItemResource.ofItemStack(emptyContainer);

            // 事前シミュレーション: 抽出可・挿入可の両方を満たすときだけ実行
            long simExtract = storage.extract(full, 1L, com.refinedmods.refinedstorage.api.core.Action.SIMULATE, actor);
            if (simExtract != 1L) return false;
            long simInsert = storage.insert(empty, 1L, com.refinedmods.refinedstorage.api.core.Action.SIMULATE, actor);
            if (simInsert != 1L) return false;

            long extracted = storage.extract(full, 1L, com.refinedmods.refinedstorage.api.core.Action.EXECUTE, actor);
            if (extracted != 1L) return false;

            matrix.setItem(slot, new ItemStack(source, 1));
            long inserted = storage.insert(empty, 1L, com.refinedmods.refinedstorage.api.core.Action.EXECUTE, actor);
            if (inserted != 1L) {
                // ストレージ満杯等: 元アイテムをストレージに戻し、スロットは空容器のままにする（何もしない挙動）
                storage.insert(full, 1L, com.refinedmods.refinedstorage.api.core.Action.EXECUTE, actor);
                matrix.setItem(slot, emptyContainer.copy());
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
