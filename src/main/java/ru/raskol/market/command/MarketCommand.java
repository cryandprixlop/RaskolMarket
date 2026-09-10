package ru.raskol.market.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.model.MarketRegion;
import ru.raskol.market.model.Stall;
import ru.raskol.market.service.MarketService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MarketCommand implements CommandExecutor, TabCompleter {

    private final RaskolMarket plugin;
    private final MarketRepository repository;
    private final MarketService service;

    public MarketCommand(RaskolMarket plugin, MarketRepository repository, MarketService service) {
        this.plugin = plugin;
        this.repository = repository;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cТолько для игроков."); return true; }
        if (args.length == 0) { sendHelp(player); return true; }
        switch (args[0].toLowerCase()) {
            case "region" -> handleRegion(player, args);
            case "stall" -> handleStall(player, args);
            case "reload" -> handleReload(player);
            case "rent" -> withStall(player, (p, s) -> service.rent(p, s));
            case "cancel" -> withStall(player, (p, s) -> service.cancel(p, s));
            case "reclaim" -> withStall(player, (p, s) -> service.reclaim(p, s));
            case "price" -> cmdPrice(player, args);
            case "info" -> handleInfo(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private interface StallAction { void run(Player p, Stall s); }

    private void withStall(Player player, StallAction action) {
        Block b = player.getTargetBlockExact(5);
        if (b == null) { player.sendMessage("§cСмотри на сундук в радиусе 5 блоков."); return; }
        Stall stall = repository.getStall(Stall.keyOf(b.getLocation()));
        if (stall == null) { player.sendMessage("§cЭто не прилавок."); return; }
        action.run(player, stall);
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== RaskolMarket ===");
        player.sendMessage("§e/market rent §7— арендовать прилавок под курсором");
        player.sendMessage("§e/market price <материал> <цена> §7— цена товара в твоей лавке");
        player.sendMessage("§e/market price §7— список цен твоей лавки");
        player.sendMessage("§e/market cancel §7— досрочно сдать лавку");
        player.sendMessage("§e/market reclaim §7— забрать остатки после истечения");
        player.sendMessage("§e/market info §7— инфо о прилавке под курсором");
        player.sendMessage("§6— Админ:");
        player.sendMessage("§e/market region create <id> [town] §7— регион (WorldEdit)");
        player.sendMessage("§e/market region town <id> <town> §7— привязать город");
        player.sendMessage("§e/market region remove <id> §7— удалить регион");
        player.sendMessage("§e/market region list §7— список регионов");
        player.sendMessage("§e/market stall add/remove/list §7— прилавки");
        player.sendMessage("§e/market reload §7— перезагрузка");
    }

    /* ---------- ЦЕНЫ ---------- */

    private void cmdPrice(Player player, String[] args) {
        Block b = player.getTargetBlockExact(5);
        if (b == null) { player.sendMessage("§cСмотри на сундук своей лавки."); return; }
        Stall stall = repository.getStall(Stall.keyOf(b.getLocation()));
        if (stall == null) { player.sendMessage("§cЭто не прилавок."); return; }
        if (!player.getUniqueId().equals(stall.getOwner()) || !stall.isRented()) {
            player.sendMessage("§cЭто не твоя арендованная лавка.");
            return;
        }
        if (args.length < 3) {
            if (stall.getPrices().isEmpty()) { player.sendMessage("§7Цен пока нет."); return; }
            player.sendMessage("§6=== Цены лавки ===");
            for (Map.Entry<String, Double> e : stall.getPrices().entrySet()) {
                player.sendMessage("§e" + e.getKey() + " §7— §e" + e.getValue() + "⚜");
            }
            return;
        }
        Material mat = Material.matchMaterial(args[1]);
        if (mat == null) { player.sendMessage("§cНеизвестный материал: " + args[1]); return; }
        double price;
        try { price = Double.parseDouble(args[2]); }
        catch (NumberFormatException e) { player.sendMessage("§cЦена должна быть числом."); return; }
        if (price <= 0) { player.sendMessage("§cЦена должна быть больше 0."); return; }
        stall.getPrices().put(mat.name(), price);
        repository.save();
        player.sendMessage("§aЦена §e" + mat.name() + " §a= §e" + price + "⚜ §aза шт.");
    }

    /* ---------- РЕГИОНЫ ---------- */

    private void handleRegion(Player player, String[] args) {
        if (!player.hasPermission("market.admin")) { player.sendMessage("§cНет прав."); return; }
        if (args.length < 2) { player.sendMessage("§cИспользуй: /market region <create|town|remove|list>"); return; }
        switch (args[1].toLowerCase()) {
            case "create" -> createRegion(player, args);
            case "town" -> setTown(player, args);
            case "remove" -> removeRegion(player, args);
            case "list" -> listRegions(player);
            default -> player.sendMessage("§cНеизвестное действие: " + args[1]);
        }
    }

    private void createRegion(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage("§cИспользуй: /market region create <id> [town]"); return; }
        String id = args[2];
        if (repository.getRegion(id) != null) { player.sendMessage("§cРегион '" + id + "' уже существует."); return; }

        Plugin wePlugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (wePlugin == null) { player.sendMessage("§cWorldEdit не найден."); return; }
        try {
            Method getSession = wePlugin.getClass().getMethod("getSession", Player.class);
            Object session = getSession.invoke(wePlugin, player);
            if (session == null) { player.sendMessage("§cВыдели регион: //wand, //pos1, //pos2."); return; }
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object weWorld = adapter.getMethod("adapt", org.bukkit.World.class).invoke(null, player.getWorld());
            Method getSelection = session.getClass().getMethod("getSelection",
                    Class.forName("com.sk89q.worldedit.world.World"));
            Object region = getSelection.invoke(session, weWorld);
            Object minV = region.getClass().getMethod("getMinimumPoint").invoke(region);
            Object maxV = region.getClass().getMethod("getMaximumPoint").invoke(region);
            Method x = minV.getClass().getMethod("x");
            Method y = minV.getClass().getMethod("y");
            Method z = minV.getClass().getMethod("z");
            MarketRegion r = new MarketRegion(id, player.getWorld().getName(),
                    ((Number) x.invoke(minV)).intValue(), ((Number) y.invoke(minV)).intValue(), ((Number) z.invoke(minV)).intValue(),
                    ((Number) x.invoke(maxV)).intValue(), ((Number) y.invoke(maxV)).intValue(), ((Number) z.invoke(maxV)).intValue());

            // Город: аргумент или автоопределение Towny
            String town = args.length >= 4 ? args[3] : service.detectTown(player.getLocation());
            r.setTownName(town);
            repository.addRegion(r);
            repository.save();
            player.sendMessage("§aРегион '" + id + "' создан!" + (town != null ? " Город: §e" + town : " §7(город не задан)"));
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause.getClass().getSimpleName().contains("Incomplete")) {
                player.sendMessage("§cВыдели регион через WorldEdit (//wand, //pos1, //pos2).");
            } else {
                player.sendMessage("§cОшибка: " + cause.getMessage());
            }
        }
    }

    private void setTown(Player player, String[] args) {
        if (args.length < 4) { player.sendMessage("§cИспользуй: /market region town <id> <town>"); return; }
        MarketRegion r = repository.getRegion(args[2]);
        if (r == null) { player.sendMessage("§cРегион не найден."); return; }
        r.setTownName(args[3]);
        repository.save();
        player.sendMessage("§aРегиону '" + r.getId() + "' назначен город: §e" + args[3]);
    }

    private void removeRegion(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage("§cИспользуй: /market region remove <id>"); return; }
        if (repository.getRegion(args[2]) == null) { player.sendMessage("§cРегион не найден."); return; }
        repository.removeRegion(args[2]);
        repository.save();
        player.sendMessage("§aРегион '" + args[2] + "' удалён.");
    }

    private void listRegions(Player player) {
        if (repository.getRegions().isEmpty()) { player.sendMessage("§7Регионов нет."); return; }
        player.sendMessage("§6=== Регионы ===");
        for (MarketRegion r : repository.getRegions()) {
            int sc = repository.getStallsInRegion(r.getId()).size();
            player.sendMessage("§e" + r.getId() + " §7(" + r.getWorld() + ") — " + sc
                    + " прилавков, город: " + (r.getTownName() != null ? "§e" + r.getTownName() : "§7—"));
        }
    }

    /* ---------- ПРИЛАВКИ ---------- */

    private void handleStall(Player player, String[] args) {
        if (!player.hasPermission("market.admin")) { player.sendMessage("§cНет прав."); return; }
        if (args.length < 2) { player.sendMessage("§cИспользуй: /market stall <add|remove|list>"); return; }
        switch (args[1].toLowerCase()) {
            case "add" -> addStall(player);
            case "remove" -> removeStall(player);
            case "list" -> listStalls(player);
            default -> player.sendMessage("§cНеизвестное действие: " + args[1]);
        }
    }

    private void addStall(Player player) {
        Block b = player.getTargetBlockExact(5);
        if (b == null || !b.getType().name().contains("CHEST")) { player.sendMessage("§cСмотри на сундук."); return; }
        String key = Stall.keyOf(b.getLocation());
        if (repository.getStall(key) != null) { player.sendMessage("§cУже прилавок."); return; }
        MarketRegion region = repository.getRegionAt(b.getLocation());
        if (region == null) { player.sendMessage("§cСундук не в регионе рынка."); return; }
        Stall st = new Stall(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        st.setRegionId(region.getId());
        repository.addStall(st);
        repository.save();
        player.sendMessage("§aПрилавок добавлен в '" + region.getId() + "'!");
    }

    private void removeStall(Player player) {
        Block b = player.getTargetBlockExact(5);
        if (b == null) { player.sendMessage("§cСмотри на блок."); return; }
        String key = Stall.keyOf(b.getLocation());
        if (repository.getStall(key) == null) { player.sendMessage("§cНе прилавок."); return; }
        repository.removeStall(key);
        repository.save();
        player.sendMessage("§aПрилавок удалён.");
    }

    private void listStalls(Player player) {
        if (repository.getStalls().isEmpty()) { player.sendMessage("§7Прилавков нет."); return; }
        player.sendMessage("§6=== Прилавки ===");
        for (Stall st : repository.getStalls()) {
            String status = st.isRented() ? "§aарендован" : "§7свободен";
            player.sendMessage("§e" + st.getKey() + " §7(" + st.getRegionId() + ") — " + status);
        }
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("market.admin")) { player.sendMessage("§cНет прав."); return; }
        plugin.reloadConfig();
        repository.load();
        player.sendMessage("§aКонфиг и данные перезагружены.");
    }

    private void handleInfo(Player player) {
        Block b = player.getTargetBlockExact(5);
        if (b == null) { player.sendMessage("§cСмотри на блок в радиусе 5 блоков."); return; }
        Stall stall = repository.getStall(Stall.keyOf(b.getLocation()));
        if (stall == null) { player.sendMessage("§7Это не прилавок."); return; }
        player.sendMessage("§6=== Прилавок ===");
        player.sendMessage("§7Координаты: §e" + stall.getX() + ", " + stall.getY() + ", " + stall.getZ());
        player.sendMessage("§7Регион: §e" + stall.getRegionId());
        if (stall.isRented()) {
            long remaining = (stall.getExpiresAt() - System.currentTimeMillis()) / 1000;
            player.sendMessage("§7Статус: §aАрендован §7(осталось §e" + (remaining / 60) + " мин§7)");
            player.sendMessage("§7Владелец: §e" + stall.getOwner());
        } else if (stall.getReclaimOwner() != null) {
            player.sendMessage("§7Статус: §6Ожидает возврата §7(/market reclaim)");
        } else {
            player.sendMessage("§7Статус: §7Свободен §7(аренда §e"
                    + plugin.getConfig().getDouble("rent.price", 500.0) + "⚜§7)");
        }
    }

    /* ---------- TAB ---------- */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(Arrays.asList("region", "stall", "reload", "rent",
                "price", "info", "reclaim", "cancel"), args[0]);
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("region")) return filter(Arrays.asList("create", "town", "remove", "list"), args[1]);
            if (args[0].equalsIgnoreCase("stall")) return filter(Arrays.asList("add", "remove", "list"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("region")
                && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("town"))) {
            List<String> ids = new ArrayList<>();
            for (MarketRegion r : repository.getRegions()) ids.add(r.getId());
            return filter(ids, args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : list) if (s.toLowerCase().startsWith(lower)) out.add(s);
        return out;
    }
}
