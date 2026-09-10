package ru.raskol.market.command;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.model.MarketRegion;
import ru.raskol.market.model.Stall;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MarketCommand implements CommandExecutor, TabCompleter {

    private final RaskolMarket plugin;
    private final MarketRepository repository;

    public MarketCommand(RaskolMarket plugin, MarketRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
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
            case "rent" -> player.sendMessage("§eФункция аренды будет на Этапе 3.");
            case "manage" -> player.sendMessage("§eФункция управления будет на Этапе 5.");
            case "price" -> player.sendMessage("§eФункция цены будет на Этапе 5.");
            case "info" -> handleInfo(player);
            case "reclaim" -> player.sendMessage("§eФункция возврата будет на Этапе 3.");
            case "cancel" -> player.sendMessage("§eФункция отмены будет на Этапе 3.");
            default -> sendHelp(player);
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== RaskolMarket ===");
        player.sendMessage("§e/market region create <id> §7— создать регион (WorldEdit)");
        player.sendMessage("§e/market region remove <id> §7— удалить регион");
        player.sendMessage("§e/market region list §7— список регионов");
        player.sendMessage("§e/market stall add §7— добавить прилавок под курсором");
        player.sendMessage("§e/market stall remove §7— удалить прилавок под курсором");
        player.sendMessage("§e/market stall list §7— список прилавков");
        player.sendMessage("§e/market info §7— инфо о прилавке под курсором");
        player.sendMessage("§e/market reload §7— перезагрузка конфига");
    }

    private void handleRegion(Player player, String[] args) {
        if (!player.hasPermission("market.admin")) {
            player.sendMessage("§cНет прав.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cИспользуй: /market region <create|remove|list>");
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "create" -> createRegion(player, args);
            case "remove" -> removeRegion(player, args);
            case "list" -> listRegions(player);
            default -> player.sendMessage("§cНеизвестное действие: " + action);
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

        try {
            Class<?> weClass = Class.forName("com.sk89q.worldedit.bukkit.WorldEditPlugin");
            Object wePlugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
            if (wePlugin == null) {
                player.sendMessage("§cWorldEdit не найден. Установи WorldEdit для создания регионов.");
                return;
            }

            LocalSession session = ((com.sk89q.worldedit.bukkit.WorldEditPlugin) wePlugin).getSession(player);
            World weWorld = BukkitAdapter.adapt(player.getWorld());
            Region region = session.getSelection(weWorld);
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();

            MarketRegion marketRegion = new MarketRegion(id,
                    player.getWorld().getName(),
                    min.x(), min.y(), min.z(),
                    max.x(), max.y(), max.z());
            repository.addRegion(marketRegion);
            repository.save();

            player.sendMessage("§aРегион '" + id + "' создан!");
            player.sendMessage("§7Координаты: (" + min.x() + "," + min.y() + "," + min.z()
                    + ") — (" + max.x() + "," + max.y() + "," + max.z() + ")");
        } catch (ClassNotFoundException | IncompleteRegionException e) {
            player.sendMessage("§cОшибка: выдели регион через WorldEdit (//wand, //pos1, //pos2).");
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

    private void listRegions(Player player) {
        if (repository.getRegions().isEmpty()) {
            player.sendMessage("§7Регионов нет.");
            return;
        }
        player.sendMessage("§6=== Регионы ===");
        for (MarketRegion r : repository.getRegions()) {
            int stallCount = repository.getStallsInRegion(r.getId()).size();
            player.sendMessage("§e" + r.getId() + " §7(" + r.getWorld()
                    + ") — " + stallCount + " прилавков");
        }
    }

    private void handleStall(Player player, String[] args) {
        if (!player.hasPermission("market.admin")) {
            player.sendMessage("§cНет прав.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cИспользуй: /market stall <add|remove|list>");
            return;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "add" -> addStall(player);
            case "remove" -> removeStall(player);
            case "list" -> listStalls(player);
            default -> player.sendMessage("§cНеизвестное действие: " + action);
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
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            player.sendMessage("§cСмотри на блок в радиусе 5 блоков.");
            return;
        }

        String key = Stall.keyOf(targetBlock.getLocation());
        Stall stall = repository.getStall(key);
        if (stall == null) {
            player.sendMessage("§7Это не прилавок.");
            return;
        }

        player.sendMessage("§6=== Прилавок ===");
        player.sendMessage("§7Координаты: §e" + stall.getX() + ", " + stall.getY() + ", " + stall.getZ());
        player.sendMessage("§7Регион: §e" + stall.getRegionId());

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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("region", "stall", "reload", "rent", "manage", "price", "info", "reclaim", "cancel"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("region")) {
                return filter(Arrays.asList("create", "remove", "list"), args[1]);
            }
            if (args[0].equalsIgnoreCase("stall")) {
                return filter(Arrays.asList("add", "remove", "list"), args[1]);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("region") && args[1].equalsIgnoreCase("remove")) {
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
