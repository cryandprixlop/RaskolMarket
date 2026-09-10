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
        if (event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        String key = Stall.keyOf(block.getLocation());
        Stall stall = repository.getStall(key);
        if (stall == null) return;

        Player player = event.getPlayer();
        event.setCancelled(true);

        if (!stall.isRented()) {
            player.sendMessage("§6Этот прилавок свободен. Используй §e/market rent §6чтобы арендовать.");
            return;
        }

        if (stall.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§aЭто ваша лавка. Используй §e/market manage §aдля управления.");
        } else {
            player.sendMessage("§eЭтот прилавок арендован. GUI покупателя будет на Этапе 4.");
        }
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
