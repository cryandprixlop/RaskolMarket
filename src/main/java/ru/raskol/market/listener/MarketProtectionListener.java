package ru.raskol.market.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.model.MarketRegion;
import ru.raskol.market.model.Stall;

public final class MarketProtectionListener implements Listener {

    private final RaskolMarket plugin;
    private final MarketRepository repository;

    public MarketProtectionListener(RaskolMarket plugin, MarketRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("market.admin")) return;
        Location loc = event.getBlock().getLocation();
        MarketRegion region = repository.getRegionAt(loc);
        if (region != null) {
            event.setCancelled(true);
            player.sendMessage("§cНельзя ломать блоки в регионе рынка!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("market.admin")) return;
        Location loc = event.getBlock().getLocation();
        MarketRegion region = repository.getRegionAt(loc);
        if (region != null) {
            event.setCancelled(true);
            player.sendMessage("§cНельзя ставить блоки в регионе рынка!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        String key = Stall.keyOf(block.getLocation());
        Stall stall = repository.getStall(key);
        if (stall == null) return;

        Player player = event.getPlayer();

        // Арендатор имеет право открывать сундук своего прилавка как обычный
        if (stall.isRented() && player.getUniqueId().equals(stall.getOwner())) {
            // не отменяем — Bukkit сам откроет vanilla chest inventory
            return;
        }

        event.setCancelled(true);

        if (!stall.isRented()) {
            player.sendMessage("§6Этот прилавок свободен. Используй §e/market rent §6чтобы арендовать.");
            return;
        }

        // GUI покупателя будет на Этапе 4
        player.sendMessage("§eЛавка арендована. GUI покупателя появится в следующем обновлении.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Location sourceLoc = event.getSource().getLocation();
        if (sourceLoc == null) return;
        String key = Stall.keyOf(sourceLoc);
        if (repository.getStall(key) != null) {
            event.setCancelled(true);
        }
    }
}
