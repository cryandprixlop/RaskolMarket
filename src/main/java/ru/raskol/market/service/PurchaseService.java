package ru.raskol.market.service;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.model.MarketRegion;
import ru.raskol.market.model.Stall;

import java.util.HashMap;

/** Логика покупки товара у арендованного прилавка. */
public final class PurchaseService {

    private final RaskolMarket plugin;
    private final MarketRepository repository;
    private final Economy economy;
    private final MarketService marketService;

    public PurchaseService(RaskolMarket plugin, MarketRepository repository,
                           Economy economy, MarketService marketService) {
        this.plugin = plugin;
        this.repository = repository;
        this.economy = economy;
        this.marketService = marketService;
    }

    /**
     * Покупка товара у прилавка.
     * @param buyer  покупатель
     * @param stall  прилавок
     * @param source предмет-образец (из сундука)
     * @param amount сколько штук хочет купить
     * @return true если покупка прошла
     */
    public boolean buy(Player buyer, Stall stall, ItemStack source, int amount) {
        if (source == null || source.getType() == Material.AIR) {
            buyer.sendMessage("§cТовар недоступен.");
            return false;
        }
        Double unitPrice = stall.getPrices().get(source.getType().name());
        if (unitPrice == null || unitPrice <= 0) {
            buyer.sendMessage("§cУ этого товара нет цены.");
            return false;
        }

        // Проверка: арендатор не может покупать у себя
        if (stall.getOwner() != null && stall.getOwner().equals(buyer.getUniqueId())) {
            buyer.sendMessage("§cНельзя покупать у самого себя.");
            return false;
        }

        // Физическое наличие в сундуке
        Block block = marketService.getBlock(stall);
        if (block == null || !(block.getState() instanceof Chest chest)) {
            buyer.sendMessage("§cСундук недоступен.");
            return false;
        }
        int available = countInChest(chest, source);
        if (available <= 0) {
            buyer.sendMessage("§cТовар закончился.");
            return false;
        }
        int toBuy = Math.min(amount, available);

        double totalCost = unitPrice * toBuy;
        EconomyResponse r = economy.withdrawPlayer(buyer, totalCost);
        if (!r.transactionSuccess()) {
            buyer.sendMessage("§cНедостаточно средств: нужно §e" + totalCost + "⚜");
            return false;
        }

        // Забираем предметы из сундука
        int taken = takeFromChest(chest, source, toBuy);
        if (taken < toBuy) {
            // Вернуть деньги за недостающее
            double refund = unitPrice * (toBuy - taken);
            economy.depositPlayer(buyer, refund);
            totalCost -= refund;
            toBuy = taken;
        }

        // Выдать покупателю
        ItemStack bought = source.clone();
        bought.setAmount(toBuy);
        HashMap<Integer, ItemStack> overflow = buyer.getInventory().addItem(bought);
        for (ItemStack drop : overflow.values()) {
            buyer.getWorld().dropItem(buyer.getLocation(), drop);
        }

        // Распределение денег
        double taxPercent = plugin.getConfig().getDouble("tax.percent", 5.0) / 100.0;
        double tax = totalCost * taxPercent;
        double sellerGets = totalCost - tax;

        // Продавцу (арендатору)
        if (stall.getOwner() != null) {
            OfflinePlayer seller = Bukkit.getOfflinePlayer(stall.getOwner());
            economy.depositPlayer(seller, sellerGets);
            Player online = seller.getPlayer();
            if (online != null) {
                online.sendMessage("§aПродано: §e" + toBuy + "x " + source.getType().name()
                        + " §aза §6" + sellerGets + "⚜ §7(игроку §e" + buyer.getName() + "§7)");
            }
        }

        // Налог в казну города
        MarketRegion region = repository.getRegion(stall.getRegionId());
        marketService.paySaleTax(region, tax);

        buyer.sendMessage("§aКуплено §e" + toBuy + "x " + source.getType().name()
                + " §aза §6" + totalCost + "⚜");

        plugin.getLogger().info("[Market] buy: " + buyer.getName() + " <- " + stall.getKey()
                + " item=" + source.getType().name() + " x" + toBuy
                + " cost=" + totalCost + " tax=" + tax);
        return true;
    }

    /** Сколько штук данного материала (по типу+durability) лежит в сундуке. */
    private int countInChest(Chest chest, ItemStack sample) {
        int total = 0;
        for (ItemStack s : chest.getInventory().getContents()) {
            if (s != null && s.getType() == sample.getType()
                    && s.getDurability() == sample.getDurability()) {
                total += s.getAmount();
            }
        }
        return total;
    }

    /** Забрать N штук из сундука. Возвращает сколько реально забрал. */
    private int takeFromChest(Chest chest, ItemStack sample, int amount) {
        int remaining = amount;
        ItemStack[] contents = chest.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack s = contents[i];
            if (s == null) continue;
            if (s.getType() != sample.getType() || s.getDurability() != sample.getDurability()) continue;
            if (s.getAmount() <= remaining) {
                remaining -= s.getAmount();
                contents[i] = null;
            } else {
                s.setAmount(s.getAmount() - remaining);
                remaining = 0;
            }
        }
        chest.getInventory().setContents(contents);
        return amount - remaining;
    }
}
