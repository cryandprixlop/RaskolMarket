package ru.raskol.market.gui;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.model.MarketRegion;
import ru.raskol.market.model.Stall;
import ru.raskol.market.service.MarketService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Витрина лавки для покупателя + обработка покупок. */
public final class StallGui implements Listener {

    private final RaskolMarket plugin;
    private final MarketRepository repository;
    private final MarketService service;

    public StallGui(RaskolMarket plugin, MarketRepository repository, MarketService service) {
        this.plugin = plugin;
        this.repository = repository;
        this.service = service;
    }

    /* ================= ОТКРЫТИЕ ВИТРИНЫ ================= */

    public void open(Player viewer, Stall stall) {
        Block block = service.getBlock(stall);
        if (block == null || !(block.getState() instanceof Chest chest)) {
            viewer.sendMessage("§cСундук лавки не найден.");
            return;
        }
        Map<String, Integer> stock = new LinkedHashMap<>();
        for (ItemStack it : chest.getInventory().getContents()) {
            if (it == null || it.getType().isAir()) continue;
            stock.merge(it.getType().name(), it.getAmount(), Integer::sum);
        }
        if (stock.isEmpty()) {
            viewer.sendMessage("§7В лавке пока нет товаров.");
            return;
        }
        int size = Math.max(9, Math.min(54, ((stock.size() + 8) / 9) * 9));
        StallGuiHolder holder = new StallGuiHolder(stall.getKey());
        Inventory inv = Bukkit.createInventory(holder, size, "§6Лавка §7" + stall.getKey());

        int slot = 0;
        int shiftStack = plugin.getConfig().getInt("buy.shift-stack", 64);
        for (Map.Entry<String, Integer> e : stock.entrySet()) {
            if (slot >= size) break;
            Material mat = Material.matchMaterial(e.getKey());
            if (mat == null) continue;
            ItemStack icon = new ItemStack(mat, 1);
            ItemMeta meta = icon.getItemMeta();
            Double price = stall.getPrices().get(e.getKey());
            List<String> lore = new ArrayList<>();
            if (price == null || price <= 0) {
                lore.add("§cЦена не назначена продавцом");
            } else {
                lore.add("§7Цена: §e" + trim(price) + "⚜ §7за шт.");
                lore.add("§7В наличии: §e" + e.getValue() + " шт.");
                lore.add("§aЛКМ: купить 1 §7| §aShift+ЛКМ: до " + shiftStack);
            }
            meta.setLore(lore);
            meta.setDisplayName("§f" + pretty(e.getKey()));
            icon.setItemMeta(meta);
            inv.setItem(slot, icon);
            holder.getSlotMaterial().put(slot, e.getKey());
            slot++;
        }
        holder.setInventory(inv);
        viewer.openInventory(inv);
    }

    /* ================= ОБРАБОТКА КЛИКОВ ================= */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof StallGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player buyer)) return;

        Stall stall = repository.getStall(holder.getStallKey());
        if (stall == null || !stall.isRented()) {
            buyer.sendMessage("§cЛавка больше не работает.");
            buyer.closeInventory();
            return;
        }
        String matName = holder.getSlotMaterial().get(event.getRawSlot());
        if (matName == null) return;

        Double price = stall.getPrices().get(matName);
        if (price == null || price <= 0) {
            buyer.sendMessage("§cЦена не назначена продавцом.");
            return;
        }
        Block block = service.getBlock(stall);
        if (block == null || !(block.getState() instanceof Chest chest)) {
            buyer.sendMessage("§cСундук не найден.");
            return;
        }
        int stock = 0;
        for (ItemStack it : chest.getInventory().getContents()) {
            if (it != null && it.getType().name().equals(matName)) stock += it.getAmount();
        }
        int want = event.isShiftClick() ? plugin.getConfig().getInt("buy.shift-stack", 64) : 1;
        int qty = Math.min(want, stock);
        if (qty <= 0) {
            buyer.sendMessage("§cТовар закончился.");
            return;
        }
        double total = round2(price * qty);
        Economy eco = plugin.getEconomy();
        EconomyResponse r = eco.withdrawPlayer(buyer, total);
        if (!r.transactionSuccess()) {
            buyer.sendMessage("§cНедостаточно средств: нужно §e" + trim(total) + "⚜");
            return;
        }

        // Вынимаем товар из сундука и запоминаем выданные стаки
        List<ItemStack> taken = new ArrayList<>();
        int left = qty;
        ItemStack[] contents = chest.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || !it.getType().name().equals(matName)) continue;
            int take = Math.min(left, it.getAmount());
            if (take >= it.getAmount()) {
                contents[i] = null;
                taken.add(it);
            } else {
                it.setAmount(it.getAmount() - take);
                ItemStack part = it.clone();
                part.setAmount(take);
                taken.add(part);
            }
            left -= take;
        }
        chest.getInventory().setContents(contents);

        // ВЫДАЧА ТОВАРА ПОКУПАТЕЛЮ (инвентарь полон -> дроп под ноги)
        for (ItemStack give : taken) {
            HashMap<Integer, ItemStack> overflow = buyer.getInventory().addItem(give);
            for (ItemStack drop : overflow.values()) {
                buyer.getWorld().dropItem(buyer.getLocation(), drop);
            }
        }

        // Выплаты: продавец 95%, налог 5%
        double taxPercent = plugin.getConfig().getDouble("tax.percent", 5.0);
        double tax = round2(total * taxPercent / 100.0);
        double sellerGet = round2(total - tax);
        if (stall.getOwner() != null) {
            eco.depositPlayer(Bukkit.getOfflinePlayer(stall.getOwner()), sellerGet);
        }
        MarketRegion region = repository.getRegion(stall.getRegionId());
        service.paySaleTax(region, tax);

        buyer.sendMessage("§aКуплено §e" + qty + " x " + pretty(matName) + " §aза §e" + trim(total) + "⚜");
        Player owner = stall.getOwner() != null ? Bukkit.getPlayer(stall.getOwner()) : null;
        if (owner != null) {
            owner.sendMessage("§aПродажа: §e" + qty + " x " + pretty(matName)
                    + " §a(+§e" + trim(sellerGet) + "⚜§a, налог §e" + trim(tax) + "⚜§a)");
        }
        plugin.getLogger().info("[Market] sale: " + buyer.getName() + " -> " + qty + "x " + matName
                + " @ " + stall.getKey() + " total=" + total + " tax=" + tax);

        open(buyer, stall); // обновить витрину
    }

    /* ================= УТИЛИТЫ ================= */

    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private String pretty(String material) {
        String[] words = material.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
