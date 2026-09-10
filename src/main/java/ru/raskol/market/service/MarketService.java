package ru.raskol.market.service;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.model.Stall;

import java.util.HashMap;
import java.util.UUID;

/** Логика рынка: аренда, досрочная сдача, возврат остатков, обработка истечений. */
public final class MarketService {

    private final RaskolMarket plugin;
    private final MarketRepository repository;
    private final Economy economy;

    public MarketService(RaskolMarket plugin, MarketRepository repository, Economy economy) {
        this.plugin = plugin;
        this.repository = repository;
        this.economy = economy;
    }

    /* ================= АРЕНДА ================= */

    public void rent(Player player, Stall stall) {
        if (stall.isRented()) {
            player.sendMessage("§cЭтот прилавок уже арендован.");
            return;
        }
        int maxPerPlayer = plugin.getConfig().getInt("rent.max-per-player", 1);
        if (maxPerPlayer > 0 && repository.countRentedBy(player.getUniqueId()) >= maxPerPlayer) {
            player.sendMessage("§cТы уже арендовал максимум лавок (" + maxPerPlayer + ").");
            return;
        }
        double price = plugin.getConfig().getDouble("rent.price", 500.0);
        long seconds = plugin.getConfig().getLong("rent.duration-seconds", 3600);

        EconomyResponse r = economy.withdrawPlayer(player, price);
        if (!r.transactionSuccess()) {
            player.sendMessage("§cНедостаточно средств: нужно §e" + price + "⚜");
            return;
        }
        stall.setOwner(player.getUniqueId());
        stall.setExpiresAt(System.currentTimeMillis() + seconds * 1000L);
        stall.setReclaimOwner(null);
        stall.setReclaimUntil(0);
        stall.getReclaimItems().clear();
        repository.save();

        player.sendMessage("§aАренда оформлена! Срок: §e" + (seconds / 60) + " минут§a.");
        player.sendMessage("§7Открой сундук и выложи товары.");
        plugin.getLogger().info("[Market] rent: " + player.getName()
                + " @ " + stall.getKey() + " price=" + price + " seconds=" + seconds);
    }

    /* ================= ДОСРОЧНАЯ СДАЧА ================= */

    public void cancel(Player player, Stall stall) {
        if (!player.getUniqueId().equals(stall.getOwner())) {
            player.sendMessage("§cЭто не ваша лавка.");
            return;
        }
        if (!stall.isRented()) {
            player.sendMessage("§cЛавка не арендована.");
            return;
        }
        long reclaimWindow = plugin.getConfig().getLong("reclaim.window-seconds", 86400);
        stall.setReclaimOwner(stall.getOwner());
        stall.setReclaimUntil(System.currentTimeMillis() + reclaimWindow * 1000L);
        stall.setOwner(null);
        stall.setExpiresAt(0);
        repository.save();
        player.sendMessage("§aЛавка сдана досрочно. Забери остатки: §e/market reclaim");
    }

    /* ================= ВОЗВРАТ ОСТАТКОВ ================= */

    public void reclaim(Player player, Stall stall) {
        if (stall.getReclaimOwner() == null || !stall.getReclaimOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cНечего забирать (или лавка не твоя).");
            return;
        }
        if (System.currentTimeMillis() > stall.getReclaimUntil()) {
            player.sendMessage("§cСрок возврата истёк.");
            stall.setReclaimOwner(null);
            stall.setReclaimUntil(0);
            repository.save();
            return;
        }
        // reclaimItems (если что-то попало сюда через код — выдаём)
        for (ItemStack item : stall.getReclaimItems()) {
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack drop : overflow.values()) {
                player.getWorld().dropItem(player.getLocation(), drop);
            }
        }
        stall.getReclaimItems().clear();
        stall.setReclaimOwner(null);
        stall.setReclaimUntil(0);
        repository.save();
        player.sendMessage("§aОстатки возвращены. Содержимое сундука можешь забрать обычным ПКМ.");
    }

    /* ================= ТИКЕР ИСТЕЧЕНИЙ ================= */

    public void tickExpired() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        String onExpire = plugin.getConfig().getString("reclaim.on-expire", "KEEP").toUpperCase();
        long reclaimWindow = plugin.getConfig().getLong("reclaim.window-seconds", 86400);

        for (Stall stall : repository.getStalls()) {
            // Истекла аренда — перевод в reclaim
            if (stall.isExpired()) {
                UUID oldOwner = stall.getOwner();
                stall.setReclaimOwner(oldOwner);
                stall.setReclaimUntil(now + reclaimWindow * 1000L);

                Block block = getBlock(stall);
                if (block != null && block.getState() instanceof Chest chest) {
                    Inventory inv = chest.getInventory();
                    if ("DROP".equals(onExpire)) {
                        for (ItemStack item : inv.getContents()) {
                            if (item != null) block.getWorld().dropItem(block.getLocation(), item);
                        }
                        inv.clear();
                    } else if ("BURN".equals(onExpire)) {
                        inv.clear();
                    }
                    // KEEP: не трогаем сундук — арендатор заберёт через reclaim/ПКМ
                }
                stall.setOwner(null);
                stall.setExpiresAt(0);
                changed = true;

                Player online = Bukkit.getPlayer(oldOwner);
                if (online != null) {
                    online.sendMessage("§cАренда лавки истекла! Забери остатки: §e/market reclaim");
                }
                plugin.getLogger().info("[Market] expired: " + stall.getKey() + " owner=" + oldOwner);
            }
            // Истёк срок reclaim — очищаем сундук
            if (stall.getReclaimOwner() != null && now > stall.getReclaimUntil()) {
                Block block = getBlock(stall);
                if (block != null && block.getState() instanceof Chest chest) {
                    chest.getInventory().clear();
                }
                plugin.getLogger().info("[Market] reclaim expired: " + stall.getKey());
                stall.setReclaimOwner(null);
                stall.setReclaimUntil(0);
                changed = true;
            }
        }
        if (changed) repository.save();
    }

    private Block getBlock(Stall stall) {
        World w = Bukkit.getWorld(stall.getWorld());
        if (w == null) return null;
        return w.getBlockAt(stall.getX(), stall.getY(), stall.getZ());
    }
}
