package ru.raskol.market.service;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.model.MarketRegion;
import ru.raskol.market.model.Stall;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** Логика рынка: аренда, сдача, возврат, истечения, налог с продаж. */
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
        if (stall.isRented()) { player.sendMessage("§cЭтот прилавок уже арендован."); return; }
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
        player.sendMessage("§7Shift+ПКМ — положить товар, ПКМ — назначить цены.");
        plugin.getLogger().info("[Market] rent: " + player.getName() + " @ " + stall.getKey()
                + " price=" + price + " seconds=" + seconds);
    }

    /* ================= СДАЧА / ВОЗВРАТ ================= */

    public void cancel(Player player, Stall stall) {
        if (!player.getUniqueId().equals(stall.getOwner())) { player.sendMessage("§cЭто не ваша лавка."); return; }
        if (!stall.isRented()) { player.sendMessage("§cЛавка не арендована."); return; }
        long window = plugin.getConfig().getLong("reclaim.window-seconds", 86400);
        stall.setReclaimOwner(stall.getOwner());
        stall.setReclaimUntil(System.currentTimeMillis() + window * 1000L);
        stall.setOwner(null);
        stall.setExpiresAt(0);
        // Main-поток (команда): переносим содержимое сундука в reclaim-хранилище
        moveChestToReclaim(stall);
        repository.save();
        player.sendMessage("§aЛавка сдана досрочно. Забери остатки: §e/market reclaim");
    }

    public void reclaim(Player player, Stall stall) {
        if (stall.getReclaimOwner() == null || !stall.getReclaimOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cНечего забирать (или лавка не твоя).");
            return;
        }
        if (System.currentTimeMillis() > stall.getReclaimUntil()) {
            player.sendMessage("§cСрок возврата истёк.");
            stall.setReclaimOwner(null);
            stall.setReclaimUntil(0);
            stall.getReclaimItems().clear();
            repository.save();
            return;
        }
        if (stall.getReclaimItems().isEmpty()) {
            player.sendMessage("§7Остатков нет — сундук был пуст.");
            stall.setReclaimOwner(null);
            stall.setReclaimUntil(0);
            repository.save();
            return;
        }
        for (ItemStack item : stall.getReclaimItems()) {
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack drop : overflow.values()) player.getWorld().dropItem(player.getLocation(), drop);
        }
        stall.getReclaimItems().clear();
        stall.setReclaimOwner(null);
        stall.setReclaimUntil(0);
        repository.save();
        player.sendMessage("§aОстатки возвращены в инвентарь.");
    }

    /* ================= НАЛОГ С ПРОДАЖ ================= */

    /** 5% (tax.percent) уходят в казну: Towny-банк города → фолбэк аккаунт → сжигание. */
    public void paySaleTax(MarketRegion region, double tax) {
        if (tax <= 0) return;
        String sink = plugin.getConfig().getString("tax.sink", "TOWN_BANK").toUpperCase();
        String town = region != null ? region.getTownName() : null;

        if ("TOWN_BANK".equals(sink) && town != null && townyDeposit(town, tax)) {
            plugin.getLogger().info("[Market] tax " + tax + " -> town bank: " + town);
            return;
        }
        if (town != null) {
            String prefix = plugin.getConfig().getString("tax.account-prefix", "town-");
            economy.depositPlayer(Bukkit.getOfflinePlayer(prefix + town), tax);
            plugin.getLogger().info("[Market] tax " + tax + " -> account: " + prefix + town);
            return;
        }
        plugin.getLogger().info("[Market] tax " + tax + " burned (у региона не задан город)");
    }

    /** Депозит в банк города Towny через рефлексию (без жёсткой зависимости). */
    private boolean townyDeposit(String townName, double amount) {
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object town = apiClass.getMethod("getTown", String.class).invoke(api, townName);
            if (town == null) return false;
            Object account = town.getClass().getMethod("getAccount").invoke(town);
            if (account == null) return false;
            Method deposit = null;
            for (Method m : account.getClass().getMethods()) {
                if (m.getName().equals("deposit") && m.getParameterCount() == 2
                        && m.getParameterTypes()[0] == double.class) { deposit = m; break; }
            }
            if (deposit == null) return false;
            Object result = deposit.invoke(account, amount, "RaskolMarket tax");
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable t) {
            plugin.getLogger().warning("Towny deposit failed: " + t.getMessage());
            return false;
        }
    }

    /** Автоопределение города Towny по координате (рефлексия). */
    public String detectTown(Location loc) {
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object town = apiClass.getMethod("getTown", Location.class).invoke(api, loc);
            if (town == null) return null;
            return (String) town.getClass().getMethod("getName").invoke(town);
        } catch (Throwable t) {
            return null;
        }
    }

    /* ================= ТИКЕР ИСТЕЧЕНИЙ ================= */

    /**
     * Async-проход: проверка времени и флагов.
     * Вся работа с блоками — внутри sync-задачи.
     */
    public void tickExpired() {
        long now = System.currentTimeMillis();
        String onExpire = plugin.getConfig().getString("reclaim.on-expire", "KEEP").toUpperCase();
        long window = plugin.getConfig().getLong("reclaim.window-seconds", 86400);

        List<Stall> expiredStalls = new ArrayList<>();
        List<Stall> reclaimExpiredStalls = new ArrayList<>();

        for (Stall stall : repository.getStalls()) {
            if (stall.isExpired()) {
                UUID oldOwner = stall.getOwner();
                stall.setReclaimOwner(oldOwner);
                stall.setReclaimUntil(now + window * 1000L);
                stall.setOwner(null);
                stall.setExpiresAt(0);
                expiredStalls.add(stall);

                Player online = Bukkit.getPlayer(oldOwner);
                if (online != null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            online.sendMessage("§cАренда лавки истекла! Забери остатки: §e/market reclaim"));
                }
                plugin.getLogger().info("[Market] expired: " + stall.getKey());
            }
            if (stall.getReclaimOwner() != null && now > stall.getReclaimUntil()) {
                reclaimExpiredStalls.add(stall);
                stall.setReclaimOwner(null);
                stall.setReclaimUntil(0);
                stall.getReclaimItems().clear();
            }
        }

        if (expiredStalls.isEmpty() && reclaimExpiredStalls.isEmpty()) return;

        // Sync-проход: работа с сундуками + сохранение
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Stall stall : expiredStalls) {
                Block block = getBlock(stall);
                if ("DROP".equals(onExpire)) {
                    if (block != null && block.getState() instanceof Chest chest) {
                        for (ItemStack item : chest.getInventory().getContents()) {
                            if (item != null) block.getWorld().dropItem(block.getLocation(), item);
                        }
                        chest.getInventory().clear();
                    }
                } else if ("BURN".equals(onExpire)) {
                    if (block != null && block.getState() instanceof Chest chest) {
                        chest.getInventory().clear();
                    }
                } else {
                    // KEEP (по умолчанию): предметы уходят в reclaim-хранилище, сундук очищается
                    moveChestToReclaim(stall);
                }
            }
            for (Stall stall : reclaimExpiredStalls) {
                Block block = getBlock(stall);
                if (block != null && block.getState() instanceof Chest chest) {
                    chest.getInventory().clear();
                }
            }
            repository.save();
        });
    }

    /* ================= ПЕРЕНОС СУНДУКА В RECLAIM ================= */

    /** Перенести всё содержимое сундука в reclaim-хранилище лавки и очистить сундук. Вызывать ТОЛЬКО в main-потоке. */
    private void moveChestToReclaim(Stall stall) {
        Block block = getBlock(stall);
        if (block == null) return;
        if (!(block.getState() instanceof Chest chest)) return;
        Inventory inv = chest.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            addToReclaim(stall, item.clone());
        }
        inv.clear();
    }

    /** Добавить предмет в reclaim-список с объединением стеков до maxStackSize. */
    private void addToReclaim(Stall stall, ItemStack add) {
        List<ItemStack> list = stall.getReclaimItems();
        int remaining = add.getAmount();
        int max = add.getMaxStackSize();
        for (ItemStack existing : list) {
            if (remaining <= 0) break;
            if (!existing.isSimilar(add)) continue;
            int space = max - existing.getAmount();
            if (space <= 0) continue;
            int move = Math.min(space, remaining);
            existing.setAmount(existing.getAmount() + move);
            remaining -= move;
        }
        while (remaining > 0) {
            int move = Math.min(remaining, max);
            ItemStack part = add.clone();
            part.setAmount(move);
            list.add(part);
            remaining -= move;
        }
    }

    /* ================= ДОСТУП К БЛОКУ ================= */

    public Block getBlock(Stall stall) {
        World w = Bukkit.getWorld(stall.getWorld());
        if (w == null) return null;
        return w.getBlockAt(stall.getX(), stall.getY(), stall.getZ());
    }
}
