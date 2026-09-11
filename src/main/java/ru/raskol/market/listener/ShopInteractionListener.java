package ru.raskol.market.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.gui.ShopView;
import ru.raskol.market.model.Stall;
import ru.raskol.market.service.PurchaseService;

public final class ShopInteractionListener implements Listener {

    private final RaskolMarket plugin;
    private final MarketRepository repository;
    private final PurchaseService purchaseService;

    public ShopInteractionListener(RaskolMarket plugin, MarketRepository repository,
                                   PurchaseService purchaseService) {
        this.plugin = plugin;
        this.repository = repository;
        this.purchaseService = purchaseService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ShopView view)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) return;
        if (event.getCurrentItem() == null) return;
        int slot = event.getSlot();

        Stall stall = view.getStall();
        if (!stall.isRented()) {
            player.sendMessage("§cПрилавок больше не арендован.");
            player.closeInventory();
            return;
        }

        ItemStack source = view.getSourceItem(slot);
        if (source == null) {
            player.sendMessage("§cТовар недоступен.");
            view.refresh();
            return;
        }

        boolean shift = event.isShiftClick();
        int amount = shift ? source.getMaxStackSize() : 1;

        boolean ok = purchaseService.buy(player, stall, source, amount);
        if (ok) {
            // Обновить витрину — предметы могли исчезнуть
            Bukkit.getScheduler().runTaskLater(plugin, view::refresh, 1L);
        }
    }

    @EventHandler
    public void onShopClose(InventoryCloseEvent event) {
        // Ничего — просто логируем для отладки
        if (!(event.getInventory().getHolder() instanceof ShopView view)) return;
        plugin.getLogger().fine("[Market] shop closed: " + view.getStall().getKey()
                + " by " + event.getPlayer().getName());
    }
}
