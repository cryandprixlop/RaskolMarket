package ru.raskol.market.command;

import org.bukkit.Bukkit;
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
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭта команда только для игроков.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "region" -> handleRegion(player, args);
            case "stall" -> handleStall(player, args);
            case "reload" -> handleReload(player);
            case "rent" -> cmdRent(player);
            case "manage" -> player.sendMessage("§eФункция управления будет на Этапе 5.");
            case "price" -> player.sendMessage("§eФункция цены будет на Этапе 5.");
            case "info" -> handleInfo(player);
            case "reclaim" -> cmdReclaim(player);
            case "cancel" -> cmdCancel(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== RaskolMarket ===");
        player.sendMessage("§e/market rent §7— арендовать прилавок под курсором");
        player.sendMessage("§e/market cancel §7— досрочно сдать лавку");
        player.sendMessage("§e/market reclaim §7— забрать остатки после истечения");
        player.sendMessage("§e/market info §7— инфо о прилавке под курсором");
        player.sendMessage("§6— Админ:");
        player.sendMessage("§e/market region create <id> §7— создать регион (WorldEdit)");
        player.sendMessage("§e/market region remove <id> §7— удалить регион");
        player.sendMessage("§e/market region list §7— список регионов");
        player.sendMessage("§e/market stall add §7— добавить прилавок под курсором");
        player.sendMessage("§e/market stall remove §7— удалить прилавок под курсором");
        player.sendMessage("§e/market stall list §7— список прилавков");
        player.sendMessage("§e/market reload §7— перезагрузка конфига");
    }

    private void cmdRent(Player player) {
        Block b = player.getTargetBlockExact(5);
        if (b == null) { player.sendMessage("§cСмотри на сундук в радиусе 5 блоков."); return; }
        Stall stall = repository.getStall(Stall.keyOf(b.getLocation()));
        if (stall == null) { player.sendMessage("§cЭто не прилавок."); return; }
        service.rent(player, stall);
    }

    private void cmdCancel(Player player) {
        Block b = player.getTargetBlockExact(5);
        if (b == null) { player.sendMessage("§cСмотри на сундук в радиусе 5 блоков."); return; }
        Stall stall = repository.getStall(Stall.keyOf(b.getLocation()));
        if (stall == null) { player.sendMessage("§cЭто не прилавок."); return; }
        service.cancel(player, stall);
    }

    private void cmdReclaim(Player player) {
        Block b = player.getTargetBlockExact(5);
        if (b == null) { player.sendMessage("§cСмотри на сундук в радиусе 5 блоков."); return; }
        Stall stall = repository.getStall(Stall.keyOf(b.getLocation()));
        if (stall == null) { player.sendMessage("§cЭто не прилавок."); return; }
        service.reclaim(player, stall);
    }

    /* ---------- РЕГИОНЫ ---------- */

    private void handleRegion(Player player, String[] args) {
        if (!player.hasPermission("market.admin")) { player.sendMessage("§cНет прав."); return; }
        if (args.length < 2) { player.sendMessage("§cИспользуй: /market region <create|remove|list>"); return; }
        switch (args[1].toLowerCase()) {
            case "create" -> createRegion(player, args);
            case "remove" -> removeRegion(player, args);
            case "list" -> listRegions(player);
            default -> player.sendMessage("§cНеизвестное действие: " + args[1]);
        }
    }

    private void createRegion(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage("§cИспользуй: /market region create <id>"); return; }
        String id = args[2];
        if (repository.getRegion(id) != null) { player.sendMessage("§cРегион '" + id + "' уже существует."); return; }

        Plugin wePlugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (wePlugin == null) { player.sendMessage("§cWorldEdit не найден."); return; }

        try {
            Method getSession = wePlugin.getClass().getMethod("getSession", Player.class);
            Object session = getSession.invoke(wePlugin, player);
            if (session == null) { player.sendMessage("§cВыдели регион: //wand, //pos1, //pos2."); return; }

            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adaptWorld = adapter.getMethod("adapt", org.bukkit.World.class);
            Object weWorld = adaptWorld.invoke(null, player.getWorld());

            Method getSelection = session.getClass().getMethod("getSelection",
                    Class.forName("com.sk89q.worldedit.world.World"));
            Object region = getSelection.invoke(session, weWorld);

            Method getMin = region.getClass().getMethod("getMinimumPoint");
            Method getMax = region.getClass().getMethod("getMaximumPoint");
            Object minV = getMin.invoke(region), maxV = getMax.invoke(region);

            Method x = minV.getClass().getMethod("x");
            Method y = minV.getClass().getMethod("y");
            Method z = minV.getClass().getMethod("z");
            int minX = ((Number) x.invoke(minV)).intValue();
            int minY = ((Number) y.invoke(minV)).intValue();
            int minZ = ((Number) z.invoke(minV)).intValue();
            int maxX = ((Number) x.invoke(maxV)).intValue();
            int maxY = ((Number) y.invoke(maxV)).intValue();
            int maxZ = ((Number) z.invoke(maxV)).intValue();

            MarketRegion r = new MarketRegion(id, player.getWorld().getName(), minX, minY, minZ, maxX, maxY, maxZ);
            repository.addRegion(r);
            repository.save();
            player.sendMessage("§aРегион '" + id + "' создан!");
            player.sendMessage("§7(" + minX + "," + minY + "," + minZ + ") — (" + maxX + "," + maxY + "," + maxZ + ")");
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause.getClass().getSimpleName().contains("Incomplete")) {
                player.sendMessage("§cВыдели регион через WorldEdit (//wand, //pos1, //pos2).");
            } else {
                player.sendMessage("§cОшибка: " + cause.getMessage());
            }
        }
    }

    private void removeRegion(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage("§cИспользуй: /market region remove <id>"); return; }
        String id = args[2];
        if (repository.getRegion(id) == null) { player.sendMessage("§cРегион не найден."); return; }
        repository.removeRegion(id);
        repository.save();
        player.sendMessage("§aРегион '" + id + "' удалён.");
    }

    private void listRegions(Player player) {
        if (repository.getRegions().isEmpty()) { player.sendMessage("§7Регионов нет."); return; }
        player.sendMessage("§6=== Регионы ===");
        for (MarketRegion r : repository.getRegions()) {
            int sc = repository.getStallsInRegion(r.getId()).size();
            player.sendMessage("§e" + r.getId() + " §7(" + r.getWorld() + ") — " + sc + " прилавков");
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
        if (b == null || !b.getType().name().contains("CHEST")) {
            player.sendMessage("§cСмотри на сундук в радиусе 5 блоков."); return;
        }
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
            player.sendMessage("§7Статус: §aАрендован");
            player.sendMessage("§7Осталось: §e" + (remaining / 60) + " мин");
            player.sendMessage("§7Владелец: §e" + stall.getOwner());
        } else if (stall.getReclaimOwner() != null) {
            long remaining = (stall.getReclaimUntil() - System.currentTimeMillis()) / 1000;
            player.sendMessage("§7Статус: §6Ожидает возврата");
            player.sendMessage("§7Забрать: §e/market reclaim §7(осталось §e" + (remaining / 3600) + " ч§7)");
        } else {
            player.sendMessage("§7Статус: §7Свободен");
            player.sendMessage("§7Аренда: §e/market rent §7(цена §e"
                    + plugin.getConfig().getDouble("rent.price", 500.0) + "⚜§7)");
        }
    }

    /* ---------- TAB COMPLETE ---------- */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("region", "stall", "reload", "rent", "manage", "price",
                    "info", "reclaim", "cancel"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("region")) return filter(Arrays.asList("create", "remove", "list"), args[1]);
            if (args[0].equalsIgnoreCase("stall")) return filter(Arrays.asList("add", "remove", "list"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("region") && args[1].equalsIgnoreCase("remove")) {
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
