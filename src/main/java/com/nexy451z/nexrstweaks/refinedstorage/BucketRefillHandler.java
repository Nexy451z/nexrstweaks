package com.nexy451z.nexrstweaks.refinedstorage;

import com.nexy451z.nexrstweaks.NexRSTweaks;
import com.refinedmods.refinedstorage.common.grid.AbstractCraftingGridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.CraftingGrid;
import com.refinedmods.refinedstorage.common.support.RecipeMatrixContainer;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.ModList;
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

    /** プレイヤーごとの状態（メニュー識別 + マトリクス前回スロット9個分） */
    private static final Map<UUID, MenuState> bucketState = new ConcurrentHashMap<>();
    /** 空容器 -> その容器を生成しうる元アイテム群（遅延構築） */
    private static volatile Map<Item, List<Item>> containerSourceMap = null;

    private static final class MenuState {
        final AbstractContainerMenu menu;
        final ItemStack[] prev;

        MenuState(AbstractContainerMenu menu, int slots) {
            this.menu = menu;
            this.prev = new ItemStack[slots];
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            bucketState.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ModList.get().isLoaded("refinedstorage")) return; // RS未導入環境ではクラス参照しない
        // 毎tick監視（「配置→クラフト」の高速連打の取りこぼしを減らす）
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
            // メニューインスタンスが変わったら状態をリセット（グリッド間の直接切替で誤補填しない）
            MenuState state = bucketState.get(player.getUUID());
            if (state == null || state.menu != menu || state.prev.length != size) {
                state = new MenuState(menu, size);
                bucketState.put(player.getUUID(), state);
            }
            ItemStack[] prev = state.prev;
            com.refinedmods.refinedstorage.common.api.storage.PlayerActor actor =
                    new com.refinedmods.refinedstorage.common.api.storage.PlayerActor(player);

            for (int i = 0; i < size; i++) {
                ItemStack cur = matrix.getItem(i).copy();
                ItemStack prevStack = prev[i];
                prev[i] = cur.copy();
                if (cur.isEmpty() || cur.getCount() != 1 || prevStack == null || prevStack.isEmpty()) continue;
                // ※同アイテム判定は入れない: 「素材→残余」への変化自体が検知対象

                // 前回が「素材」で今回が「その残余」のときのみ補填する（プレイヤーが最初から置いた空容器は触らない）
                Item expectedEmpty = prevStack.getItem().getCraftingRemainingItem();
                if (expectedEmpty == null || expectedEmpty != cur.getItem()) continue;

                // 補填元は「実際にそのスロットにあった素材」を最優先（別種バケツが静かに入るのを防ぐ）
                if (tryRefillFromStorage(player, storage, actor, matrix, i, cur, prevStack.getItem())) {
                    continue;
                }
                // 前回素材がストレージになければ、同種残余を持つ他のアイテムで代替する
                List<Item> sources = containerSources().get(cur.getItem());
                if (sources == null || sources.isEmpty()) continue;
                for (Item source : sources) {
                    if (source == prevStack.getItem()) continue;
                    if (tryRefillFromStorage(player, storage, actor, matrix, i, cur, source)) {
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean tryRefillFromStorage(
            ServerPlayer player,
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
                // ストレージ満杯等: 元アイテムをストレージに戻せない場合はプレイヤーに渡す（消失しない安全側）
                matrix.setItem(slot, emptyContainer.copy());
                ItemStack refund = new ItemStack(source, (int) extracted);
                if (!player.getInventory().add(refund)) {
                    player.drop(refund, false);
                }
                return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
