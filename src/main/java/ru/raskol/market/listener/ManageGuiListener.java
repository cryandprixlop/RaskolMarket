package ru.raskol.market.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.gui.ManageGui;
import ru.raskol.market.model.Stall;

public final class ManageGuiListener implements Listener {

    private final RaskolMarket plugin;
    private final MarketRepository repository;
    private final PriceInputListener priceInputListener;

    public ManageGuiListener(RaskolMarket plugin, MarketRepository repository,
                             PriceInputListener priceInputListener) {
        this.plugin = plugin;
        this.repository = repository;
        this.priceInputListener = priceInputListener;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onManageClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof ManageGui gui)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getInventory()) return;
        if (event.getCurrentItem() == null) return;

        int slot = event.getSlot();
        ItemStack clicked = event.getCurrentItem();
        org.bukkit.Material material = clicked.getType();

        Stall stall = gui.getStall();
        if (!stall.isRented()) {
            player.sendMessage("§cПрилавок больше не арендован.");
            player.closeInventory();
            return;
        }

        if (stall.getOwner() == null || !stall.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cЭто не ваша лавка.");
            player.closeInventory();
            return;
        }

        // Закрыть GUI и запросить ввод цены в чат
        player.closeInventory();
        priceInputListener.requestPriceInput(player, stall, material);
    }
}
