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

        // Арендатор — открыть управление сундуком (пока пишем подсказку, GUI будет на Этапе 5)
        if (stall.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§aЭто ваша лавка. Управление ценами: §e/market price <материал> <цена>");
            player.sendMessage("§7Открой сундук обычным Shift+ПКМ, чтобы выложить товар.");
            return;
        }

        // Покупатель — открыть витрину
        String ownerName = null;
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(stall.getOwner());
        if (op.getName() != null) ownerName = op.getName();

        ru.raskol.market.gui.ShopView view = new ru.raskol.market.gui.ShopView(stall, ownerName);
        player.openInventory(view.getInventory());
    }
