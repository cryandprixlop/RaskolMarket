package ru.raskol.market.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.gui.ManageGui;
import ru.raskol.market.model.Stall;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Перехватывает следующее сообщение игрока в чате, если он в режиме ввода цены.
 * Формат: число (цена за 1 шт) или "отмена" для отмены.
 */
public final class PriceInputListener implements Listener {

    private final RaskolMarket plugin;
    private final MarketRepository repository;

    // Игрок -> ожидаемый материал для установки цены
    private final Map<UUID, PendingInput> pendingInputs = new HashMap<>();

    public PriceInputListener(RaskolMarket plugin, MarketRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /** Запросить ввод цены для материала. */
    public void requestPriceInput(Player player, Stall stall, org.bukkit.Material material) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(stall, material));
        player.sendMessage("§6Введите цену за 1 шт §e" + material.name() + " §6в чат (число):");
        player.sendMessage("§7Или напишите §eотмена §7для отмены.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PendingInput pending = pendingInputs.get(player.getUniqueId());
        if (pending == null) return;

        event.setCancelled(true);
        pendingInputs.remove(player.getUniqueId());

        String message = event.getMessage().trim();
        if (message.equalsIgnoreCase("отмена") || message.equalsIgnoreCase("cancel")) {
            player.sendMessage("§cУстановка цены отменена.");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(message.replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage("§cНекорректное число: §e" + message);
            return;
        }

        if (price <= 0) {
            player.sendMessage("§cЦена должна быть положительной.");
            return;
        }

        // Установить цену
        String matName = pending.material.name();
        pending.stall.getPrices().put(matName, price);
        repository.save();

        player.sendMessage("§aЦена установлена: §e" + matName + " §a= §6" + price + "⚜ §aза 1 шт");
        plugin.getLogger().info("[Market] price set: " + player.getName() + " @ "
                + pending.stall.getKey() + " item=" + matName + " price=" + price);

        // Обновить GUI управления (если игрок ещё в нём)
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ManageGui gui) {
                gui.refresh();
            }
        });
    }

    private static final class PendingInput {
        final Stall stall;
        final org.bukkit.Material material;

        PendingInput(Stall stall, org.bukkit.Material material) {
            this.stall = stall;
            this.material = material;
        }
    }
}
