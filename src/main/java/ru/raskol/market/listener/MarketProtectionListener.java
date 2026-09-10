package ru.raskol.market.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.gui.StallGui;
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
        if (repository.getRegionAt(loc) != null) {
            event.setCancelled(true);
            player.sendMessage("§cНельзя ломать блоки в регионе рынка!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("market.admin")) return;
        Location loc = event.getBlock().getLocation();
        if (repository.getRegionAt(loc) != null) {
            event.setCancelled(true);
            player.sendMessage("§cНельзя ставить блоки в регионе рынка!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Stall stall = repository.getStall(Stall.keyOf(block.getLocation()));
        if (stall == null) return;

        Player player = event.getPlayer();

        // Владелец открывает сундук как обычный инвентарь
        if (stall.isRented() && player.getUniqueId().equals(stall.getOwner())) return;

        event.setCancelled(true);

        if (!stall.isRented()) {
            player.sendMessage("§6Прилавок свободен. Аренда: §e/market rent");
            return;
        }
        // Покупатель видит витрину
        new StallGui(plugin, repository, plugin.getService()).open(player, stall);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        Location sourceLoc = event.getSource().getLocation();
        if (sourceLoc == null) return;
        if (repository.getStall(Stall.keyOf(sourceLoc)) != null) event.setCancelled(true);
    }
}
