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
import ru.raskol.market.gui.ManageGui;
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
    private final MarketService marketService;

    public MarketCommand(RaskolMarket plugin, MarketRepository repository, MarketService marketService) {
        this.plugin = plugin;
        this.repository = repository;
        this.marketService = marketService;
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
            case "rent" -> withStall(player, stall -> marketService.rent(player, stall));
            case "cancel" -> withStall(player, stall -> marketService.cancel(player, stall));
            case "reclaim" -> withStall(player, stall -> marketService.reclaim(player, stall));
            case "manage" -> handleManage(player);
            case "price" -> player.sendMessage("§7Цены назначаются через GUI: ПКМ по своему прилавку → клик по предмету → цена в чат.");
            case "info" -> handleInfo(player);
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== RaskolMarket ===");
        player.sendMessage("§e/market rent §7— арендовать прилавок под курсором");
        player.sendMessage("§e/market manage §7— GUI управления ценами");
        player.sendMessage("§e/market cancel §7— сдать лавку досрочно");
        player.sendMessage("§e/market reclaim §7— забрать остатки после истечения");
        player.sendMessage("§e/market info §7— инфо о прилавке под курсором");
        if (player.hasPermission("market.admin")) {
            player.sendMessage("§e/market region create <id> §7— создать регион (WorldEdit)");
            player.sendMessage("§e/market region remove <id> §7— удалить регион");
            player.sendMessage("§e/market region town <id> <город> §7— привязать город");
            player.sendMessage("§e/market region list §7— список регионов");
            player.sendMessage("§e/market stall add/remove/list §7— прилавки");
            player.sendMessage("§e/market reload §7— перезагрузка");
        }
    }

    /* ================= РЕГИОНЫ ================= */

    private void handleRegion(Player player, String[] args) {
        if (!player.hasPermission("market.admin")) {
            player.sendMessage("§cНет прав.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cИспользуй: /market region <create|remove|town|list>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "create" -> createRegion(player, args);
            case "remove" -> removeRegion(player, args);
            case "town" -> setRegionTown(player, args);
            case "list" -> listRegions(player);
            default -> player.sendMessage("§cНеизвестное действие: " + args[1]);
        }
    }

    private void createRegion(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cИспользуй: /market region create <id>");
            return;
        }
        String id = args[2];
        if (repository.getRegion(id) != null) {
            player.sendMessage("§cРегион с id '" + id + "' уже существует.");
            return;
        }

        Plugin wePlugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (wePlugin == null) {
            player.sendMessage("§cWorldEdit не найден. Установи WorldEdit для создания регионов.");
            return;
        }

        try {
            Method getSession = wePlugin.getClass().getMethod("getSession", Player.class);
            Object session = getSession.invoke(wePlugin, player);
            if (session == null) {
                player.sendMessage("§cНе удалось получить сессию WorldEdit. Выдели регион: //wand, //pos1, //pos2.");
                return;
            }

            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Method adaptWorld = bukkitAdapterClass.getMethod("adapt", org.bukkit.World.class);
            Object weWorld = adaptWorld.invoke(null, player.getWorld());

            Method getSelection = session.getClass().getMethod("getSelection",
                    Class.forName("com.sk89q.worldedit.world.World"));
            Object region = getSelection.invoke(session, weWorld);

            Method getMinPoint = region.getClass().getMethod("getMinimumPoint");
            Method getMaxPoint = region.getClass().getMethod("getMaximumPoint");
            Object minVec = getMinPoint.invoke(region);
            Object maxVec = getMaxPoint.invoke(region);

            Method mx = minVec.getClass().getMethod("x");
            Method my = minVec.getClass().getMethod("y");
            Method mz = minVec.getClass().getMethod("z");
            int minX = ((Number) mx.invoke(minVec)).intValue();
            int minY = ((Number) my.invoke(minVec)).intValue();
            int minZ = ((Number) mz.invoke(minVec)).intValue();
            int maxX = ((Number) mx.invoke(maxVec)).intValue();
            int maxY = ((Number) my.invoke(maxVec)).intValue();
            int maxZ = ((Number) mz.invoke(maxVec)).intValue();

            MarketRegion marketRegion = new MarketRegion(id,
                    player.getWorld().getName(),
                    minX, minY, minZ,
                    maxX, maxY, maxZ);

            // ЭТАП 6: автопривязка города Towny по координате игрока
            String town = marketService.detectTown(player.getLocation());
            if (town != null) {
                marketRegion.setTownName(town);
            }

            repository.addRegion(marketRegion);
            repository.save();

            player.sendMessage("§aРегион '" + id + "' создан!");
            player.sendMessage("§7Координаты: (" + minX + "," + minY + "," + minZ
                    + ") — (" + maxX + "," + maxY + "," + maxZ + ")");
            if (town != null) {
                player.sendMessage("§7Город (Towny): §e" + town + " §7— налог 5% пойдёт в его банк.");
            } else {
                player.sendMessage("§7Регион вне города Towny. Привяжи вручную: §e/market region town " + id + " <город>");
            }
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String msg = cause.getClass().getSimpleName();
            if (msg.contains("Incomplete")) {
                player.sendMessage("§cОшибка: выдели регион через WorldEdit (//wand, //pos1, //pos2).");
            } else {
                player.sendMessage("§cОшибка создания региона: " + cause.getMessage());
                plugin.getLogger().warning("createRegion error: " + e.getMessage());
            }
        }
    }

    private void removeRegion(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cИспользуй: /market region remove <id>");
            return;
        }
        String id = args[2];
        if (repository.getRegion(id) == null) {
            player.sendMessage("§cРегион '" + id + "' не найден.");
            return;
        }
        repository.removeRegion(id);
        repository.save();
        player.sendMessage("§aРегион '" + id + "' удалён.");
    }

    private void setRegionTown(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§cИспользуй: /market region town <id> <город>");
            return;
        }
        MarketRegion r = repository.getRegion(args[2]);
        if (r == null) {
            player.sendMessage("§cРегион '" + args[2] + "' не найден.");
            return;
        }
        r.setTownName(args[3]);
        repository.save();
        player.sendMessage("§aРегиону '" + r.getId() + "' назначен город: §e" + args[3]);
    }

    private void listRegions(Player player) {
        if (repository.getRegions().isEmpty()) {
            player.sendMessage("§7Регионов нет.");
            return;
        }
        player.sendMessage("§6=== Регионы ===");
        for (MarketRegion r : repository.getRegions()) {
            int stallCount = repository.getStallsInRegion(r.getId()).size();
            String town = r.getTownName() != null ? r.getTownName() : "§7нет";
            player.sendMessage("§e" + r.getId() + " §7(" + r.getWorld() + ") — "
                    + stallCount + " прилавков, город: §e" + town);
        }
    }

    /* ================= ПРИЛАВКИ ================= */

    private void handleStall(Player player, String[] args) {
        if (!player.hasPermission("market.admin")) {
            player.sendMessage("§cНет прав.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cИспользуй: /market stall <add|remove|list>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "add" -> addStall(player);
            case "remove" -> removeStall(player);
            case "list" -> listStalls(player);
            default -> player.sendMessage("§cНеизвестное действие: " + args[1]);
        }
    }

    private void addStall(Player player) {
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !targetBlock.getType().name().contains("CHEST")) {
            player.sendMessage("§cСмотри на сундук в радиусе 5 блоков.");
            return;
        }

        String key = Stall.keyOf(targetBlock.getLocation());
        if (repository.getStall(key) != null) {
            player.sendMessage("§cЭтот сундук уже является прилавком.");
            return;
        }

        MarketRegion region = repository.getRegionAt(targetBlock.getLocation());
        if (region == null) {
            player.sendMessage("§cЭтот сундук не находится в регионе рынка.");
            return;
        }

        Stall stall = new Stall(targetBlock.getWorld().getName(),
                targetBlock.getX(), targetBlock.getY(), targetBlock.getZ());
        stall.setRegionId(region.getId());
        repository.addStall(stall);
        repository.save();

        player.sendMessage("§aПрилавок добавлен в регион '" + region.getId() + "'!");
    }

    private void removeStall(Player player) {
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            player.sendMessage("§cСмотри на блок в радиусе 5 блоков.");
            return;
        }

        String key = Stall.keyOf(targetBlock.getLocation());
        if (repository.getStall(key) == null) {
            player.sendMessage("§cЭтот блок не является прилавком.");
            return;
        }

        repository.removeStall(key);
        repository.save();
        player.sendMessage("§aПрилавок удалён.");
    }

    private void listStalls(Player player) {
        if (repository.getStalls().isEmpty()) {
            player.sendMessage("§7Прилавков нет.");
            return;
        }
        player.sendMessage("§6=== Прилавки ===");
        for (Stall st : repository.getStalls()) {
            String status = st.isRented() ? "§aарендован" : "§7свободен";
            player.sendMessage("§e" + st.getKey() + " §7(" + st.getRegionId() + ") — " + status);
        }
    }

    /* ================= ИГРОВЫЕ ДЕЙСТВИЯ ================= */

    private void withStall(Player player, java.util.function.Consumer<Stall> action) {
        Stall stall = targetStall(player);
        if (stall == null) return;
        action.accept(stall);
    }

    private Stall targetStall(Player player) {
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            player.sendMessage("§cСмотри на прилавок в радиусе 5 блоков.");
            return null;
        }
        Stall stall = repository.getStall(Stall.keyOf(targetBlock.getLocation()));
        if (stall == null) {
            player.sendMessage("§cЭто не прилавок рынка.");
            return null;
        }
        return stall;
    }

    private void handleManage(Player player) {
        Stall stall = targetStall(player);
        if (stall == null) return;
        if (!stall.isRented() || !player.getUniqueId().equals(stall.getOwner())) {
            player.sendMessage("§cЭто не ваша лавка.");
            return;
        }
        player.openInventory(new ManageGui(stall).getInventory());
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("market.admin")) {
            player.sendMessage("§cНет прав.");
            return;
        }
        plugin.reloadConfig();
        repository.load();
        player.sendMessage("§aКонфиг и данные перезагружены.");
    }

    private void handleInfo(Player player) {
        Stall stall = targetStall(player);
        if (stall == null) return;

        player.sendMessage("§6=== Прилавок ===");
        player.sendMessage("§7Координаты: §e" + stall.getX() + ", " + stall.getY() + ", " + stall.getZ());
        MarketRegion region = repository.getRegion(stall.getRegionId());
        player.sendMessage("§7Регион: §e" + stall.getRegionId());
        if (region != null) {
            String town = region.getTownName() != null ? region.getTownName() : "§7нет";
            player.sendMessage("§7Город (налог): §e" + town);
        }

        if (stall.isRented()) {
            long remaining = (stall.getExpiresAt() - System.currentTimeMillis()) / 1000;
            player.sendMessage("§7Статус: §aАрендован");
            player.sendMessage("§7Осталось: §e" + remaining + " сек");
            player.sendMessage("§7Владелец: §e" + stall.getOwner());
        } else {
            player.sendMessage("§7Статус: §7Свободен");
            player.sendMessage("§7Аренда: §e/market rent");
        }
    }

    /* ================= TAB COMPLETE ================= */

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("region", "stall", "reload", "rent", "manage",
                    "price", "info", "reclaim", "cancel"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("region")) {
                return filter(Arrays.asList("create", "remove", "town", "list"), args[1]);
            }
            if (args[0].equalsIgnoreCase("stall")) {
                return filter(Arrays.asList("add", "remove", "list"), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("region")
                && (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("town"))) {
            List<String> ids = new ArrayList<>();
            for (MarketRegion r : repository.getRegions()) {
                ids.add(r.getId());
            }
            return filter(ids, args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(lower)) out.add(s);
        }
        return out;
    }
}
