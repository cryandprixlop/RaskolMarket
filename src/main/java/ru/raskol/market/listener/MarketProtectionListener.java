package ru.raskol.market.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.EquipmentSlot;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.gui.ShopView;
import ru.raskol.market.model.MarketRegion;
import ru.raskol.market.model.Stall;

/**
 * Защита региона рынка и прилавков + маршрутизация ПКМ:
 * свободный прилавок -> подсказка аренды,
 * владелец -> подсказка / Shift+ПКМ открывает настоящий сундук,
 * покупатель -> GUI-витрина ShopView.
 */
public final class MarketProtectionListener implements Listener {

    private final RaskolMarket plugin;
    private final MarketRepository repository;

    public MarketProtectionListener(RaskolMarket plugin, MarketRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /* ================= ЗАЩИТА РЕГИОНА ================= */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("market.admin")) return;
        MarketRegion region = repository.getRegionAt(event.getBlock().getLocation());
        if (region != null) {
            event.setCancelled(true);
            player.sendMessage("§cНельзя ломать блоки в регионе рынка!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("market.admin")) return;
        MarketRegion region = repository.getRegionAt(event.getBlock().getLocation());
        if (region != null) {
            event.setCancelled(true);
            player.sendMessage("§cНельзя ставить блоки в регионе рынка!");
        }
    }

    /* ================= ЗАЩИТА ПРИЛАВКОВ ОТ ВОРОНОК ================= */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Location sourceLoc = event.getSource().getLocation();
        if (sourceLoc == null) return;
        if (repository.getStall(Stall.keyOf(sourceLoc)) != null) {
            event.setCancelled(true);
        }
    }

    /* ================= ВЗАИМОДЕЙСТВИЕ С ПРИЛАВКОМ ================= */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // защита от двойного срабатывания (off-hand)
        if (event.getClickedBlock() == null) return;

        Block block = event.getClickedBlock();
        Stall stall = repository.getStall(Stall.keyOf(block.getLocation()));
        if (stall == null) return;

        Player player = event.getPlayer();

        // --- Прилавок свободен ---
        if (!stall.isRented()) {
            event.setCancelled(true);
            player.sendMessage("§6Этот прилавок свободен. Аренда: §e/market rent");
            return;
        }

        // --- Владелец прилавка ---
        if (stall.getOwner() != null && stall.getOwner().equals(player.getUniqueId())) {
            if (player.isSneaking()) {
                // Shift+ПКМ: НЕ отменяем событие — откроется настоящий сундук для пополнения товара
                return;
            }
            event.setCancelled(true);
            player.sendMessage("§aЭто ваша лавка.");
            player.sendMessage("§7Пополнить товар: §eShift+ПКМ §7по сундуку.");
            player.sendMessage("§7Назначить цену: §e/market price <материал> <цена>");
            return;
        }

        // --- Покупатель: открываем GUI-витрину ---
        event.setCancelled(true);
        String ownerName = null;
        OfflinePlayer op = Bukkit.getOfflinePlayer(stall.getOwner());
        if (op != null) ownerName = op.getName();

        ShopView view = new ShopView(stall, ownerName);
        player.openInventory(view.getInventory());
    }
}
