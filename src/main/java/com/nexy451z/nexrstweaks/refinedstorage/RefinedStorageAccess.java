package com.nexy451z.nexrstweaks.refinedstorage;

import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.common.grid.AbstractCraftingGridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import com.refinedmods.refinedstorage.common.grid.CraftingGrid;
import com.refinedmods.refinedstorage.common.api.grid.Grid;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;

/**
 * Refined Storage 2.0.9 との型付きアクセスヘルパー。
 * RS本体の grid / craftingGrid フィールドは private かつ publicアクセサが存在しないため、
 * この2つのフィールド読み取りのみリフレクション（デコンパイル実物で確認済み）。
 * Field は1回だけ解決してキャッシュ。失敗時は1度だけWARNを出す（将来のRS更新での無音劣化を検知可能に）。
 */
public final class RefinedStorageAccess {
    private RefinedStorageAccess() {
    }

    private static volatile Field gridField = null;
    private static volatile Field craftingGridField = null;
    private static volatile boolean warnedGridField = false;
    private static volatile boolean warnedCraftingGridField = false;

    public static boolean isRsContainer(ServerPlayer player) {
        if (player == null) return false;
        return player.containerMenu instanceof AbstractGridContainerMenu;
    }

    /** プレイヤーが開いているRSグリッドのネットワークストレージを取得する */
    public static Storage getGridStorage(ServerPlayer player) {
        if (!isRsContainer(player)) return null;
        try {
            AbstractGridContainerMenu menu = (AbstractGridContainerMenu) player.containerMenu;
            Grid grid = getGrid(menu);
            return grid != null ? grid.getItemStorage() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Grid getGrid(AbstractGridContainerMenu menu) {
        try {
            Field f = gridField;
            if (f == null) {
                f = AbstractGridContainerMenu.class.getDeclaredField("grid");
                f.setAccessible(true);
                gridField = f;
            }
            Object grid = f.get(menu);
            return grid instanceof Grid g ? g : null;
        } catch (Throwable t) {
            if (!warnedGridField) {
                warnedGridField = true;
                com.nexy451z.nexrstweaks.NexRSTweaks.LOGGER.warn("[NexRSTweaks] failed to resolve RS 'grid' field (feature disabled)", t);
            }
            return null;
        }
    }

    /** プレイヤーが開いているRSクラフトグリッド（マトリクス・結果スロット管理）を取得する */
    public static CraftingGrid getCraftingGrid(AbstractCraftingGridContainerMenu menu) {
        try {
            Field f = craftingGridField;
            if (f == null) {
                f = AbstractCraftingGridContainerMenu.class.getDeclaredField("craftingGrid");
                f.setAccessible(true);
                craftingGridField = f;
            }
            Object craftingGrid = f.get(menu);
            return craftingGrid instanceof CraftingGrid g ? g : null;
        } catch (Throwable t) {
            if (!warnedCraftingGridField) {
                warnedCraftingGridField = true;
                com.nexy451z.nexrstweaks.NexRSTweaks.LOGGER.warn("[NexRSTweaks] failed to resolve RS 'craftingGrid' field (feature disabled)", t);
            }
            return null;
        }
    }
}
