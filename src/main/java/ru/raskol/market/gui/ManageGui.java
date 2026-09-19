package ru.raskol.market.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.raskol.market.model.Stall;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI управления ценами для владельца прилавка.
 * Показывает все предметы из сундука, клик по предмету → запрос цены в чат.
 */
public final class ManageGui implements InventoryHolder {

    private final Inventory inventory;
    private final Stall stall;

    public ManageGui(Stall stall) {
        this.stall = stall;
        this.inventory = Bukkit.createInventory(this, 54, "§0Управление прилавком");
        refresh();
    }

    /** Обновить содержимое GUI из реального сундука. */
    public void refresh() {
        inventory.clear();
        org.bukkit.World w = Bukkit.getWorld(stall.getWorld());
        if (w == null) return;
        Block block = w.getBlockAt(stall.getX(), stall.getY(), stall.getZ());
        if (!(block.getState() instanceof Chest chest)) return;

        int slot = 0;
        Map<String, Double> prices = stall.getPrices();

        for (ItemStack source : chest.getInventory().getContents()) {
            if (source == null || source.getType() == Material.AIR) continue;
            if (slot >= 45) break;

            String matName = source.getType().name();
            Double price = prices.get(matName);

            ItemStack display = source.clone();
            display.setAmount(1);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                if (lore == null) lore = new ArrayList<>();
                lore.add("§7──────────────");
                if (price != null && price > 0) {
                    lore.add("§aТекущая цена: §6" + formatPrice(price) + "⚜ §7за 1 шт");
                    lore.add("§7ЛКМ — изменить цену");
                } else {
                    lore.add("§cЦена не установлена");
                    lore.add("§7ЛКМ — установить цену");
                }
                lore.add("§7В сундуке: §e" + source.getAmount() + " шт");
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(slot++, display);
        }

        // Заглушки для пустых слотов
        for (int i = slot; i < 45; i++) {
            inventory.setItem(i, null);
        }
    }

    public Stall getStall() { return stall; }

    @Override
    public Inventory getInventory() { return inventory; }

    /** Получить предмет по слоту (для определения материала при установке цены). */
    public Material getMaterialAt(int slot) {
        ItemStack display = inventory.getItem(slot);
        if (display == null) return null;
        return display.getType();
    }

    private static String formatPrice(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
