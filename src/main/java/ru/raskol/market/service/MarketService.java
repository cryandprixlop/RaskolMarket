package ru.raskol.market.service;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
        player.sendMessage("§7Открой сундук, выложи товары, назначь цены: §e/market price <материал> <цена>");
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
        player.sendMessage("§aОстатки возвращены. Содержимое сундука забери обычным ПКМ.");
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

    /* ================= ТИКЕР ИСТЕЧЕНИЙ (АСИНХРОННАЯ ЧАСТЬ) ================= */

    /**
     * Проверяет истечения и собирает список лавок для обработки.
     * Вызывается из async-потока, НЕ работает с блоками.
     */
    public void tickExpired() {
        long now = System.currentTimeMillis();
        String onExpire = plugin.getConfig().getString("reclaim.on-expire", "KEEP").toUpperCase();
        long window = plugin.getConfig().getLong("reclaim.window-seconds", 86400);

        List<Stall> expiredStalls = new ArrayList<>();
        List<Stall> reclaimExpiredStalls = new ArrayList<>();
        boolean changed = false;

        // Проход 1: собираем истёкшие лавки (async, без работы с блоками)
        for (Stall stall : repository.getStalls()) {
            if (stall.isExpired()) {
                UUID oldOwner = stall.getOwner();
                stall.setReclaimOwner(oldOwner);
                stall.setReclaimUntil(now + window * 1000L);
                stall.setOwner(null);
                stall.setExpiresAt(0);
                expiredStalls.add(stall);
                changed = true;
                
                Player online = Bukkit.getPlayer(oldOwner
