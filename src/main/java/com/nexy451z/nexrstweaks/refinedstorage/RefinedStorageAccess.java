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
 */
public final class RefinedStorageAccess {
    private RefinedStorageAccess() {
    }

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
            Field gridField = AbstractGridContainerMenu.class.getDeclaredField("grid");
            gridField.setAccessible(true);
            Object grid = gridField.get(menu);
            return grid instanceof Grid g ? g : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** プレイヤーが開いているRSクラフトグリッド（マトリクス・結果スロット管理）を取得する */
    public static CraftingGrid getCraftingGrid(AbstractCraftingGridContainerMenu menu) {
        try {
            Field craftingGridField = AbstractCraftingGridContainerMenu.class.getDeclaredField("craftingGrid");
            craftingGridField.setAccessible(true);
            Object craftingGrid = craftingGridField.get(menu);
            return craftingGrid instanceof CraftingGrid g ? g : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
