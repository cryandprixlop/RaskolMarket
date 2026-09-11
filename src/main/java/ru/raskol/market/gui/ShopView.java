package ru.raskol.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.raskol.market.model.Stall;

import java.util.ArrayList;
import java.util.List;

/**
 * Виртуальная витрина прилавка для покупателей.
 * Содержимое — копия сундука, цены берутся из stall.getPrices().
 * Клик по предмету = покупка.
 */
public final class ShopView implements InventoryHolder {

    private final Inventory inventory;
    private final Stall stall;

    public ShopView(Stall stall, String ownerName) {
        this.stall = stall;
        this.inventory = Bukkit.createInventory(this, 54,
                "§0Прилавок §7| §f" + (ownerName != null ? ownerName : "неизвестно"));
        refresh();
    }

    /** Обновить содержимое витрины из реального сундука. */
    public void refresh() {
        inventory.clear();
        org.bukkit.World w = Bukkit.getWorld(stall.getWorld());
        if (w == null) return;
        org.bukkit.block.Block block = w.getBlockAt(stall.getX(), stall.getY(), stall.getZ());
        if (!(block.getState() instanceof org.bukkit.block.Chest chest)) return;

        int slot = 0;
        for (ItemStack source : chest.getInventory().getContents()) {
            if (source == null || source.getType() == Material.AIR) continue;
            if (slot >= 45) break;

            Double price = stall.getPrices().get(source.getType().name());
            if (price == null || price <= 0) continue;

            ItemStack display = source.clone();
            display.setAmount(1);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                if (lore == null) lore = new ArrayList<>();
                lore.add("§7──────────────");
                lore.add("§eЦена за 1 шт: §6" + formatPrice(price) + "⚜");
                lore.add("§7ЛКМ — купить 1 шт");
                lore.add("§7Shift+ЛКМ — купить стек (" + source.getMaxStackSize() + ")");
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(slot++, display);
        }
    }

    public Stall getStall() { return stall; }

    @Override
    public Inventory getInventory() { return inventory; }

    /** Позиция предмета в витрине → ItemStack из сундука (копия). */
    public ItemStack getSourceItem(int slot) {
        ItemStack display = inventory.getItem(slot);
        if (display == null) return null;
        org.bukkit.World w = Bukkit.getWorld(stall.getWorld());
        if (w == null) return null;
        org.bukkit.block.Block block = w.getBlockAt(stall.getX(), stall.getY(), stall.getZ());
        if (!(block.getState() instanceof org.bukkit.block.Chest chest)) return null;

        for (ItemStack s : chest.getInventory().getContents()) {
            if (s != null && s.getType() == display.getType()
                    && s.getDurability() == display.getDurability()) {
                return s.clone();
            }
        }
        return null;
    }

    private static String formatPrice(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
