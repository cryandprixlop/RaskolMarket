package ru.raskol.market;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.market.command.MarketCommand;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.listener.MarketProtectionListener;
import ru.raskol.market.listener.ShopInteractionListener;
import ru.raskol.market.service.MarketService;
import ru.raskol.market.service.PurchaseService;

public final class RaskolMarket extends JavaPlugin {

    private MarketRepository repository;
    private MarketService marketService;
    private PurchaseService purchaseService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Economy economy = setupEconomy();
        if (economy == null) {
            getLogger().severe("Vault/экономика не найдена — плагин отключён.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        repository = new MarketRepository(this);
        repository.load();

        marketService = new MarketService(this, repository, economy);
        purchaseService = new PurchaseService(this, repository, economy, marketService);

        PluginCommand cmd = getCommand("market");
        if (cmd != null) {
            MarketCommand executor = new MarketCommand(this, repository);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(
                new MarketProtectionListener(this, repository), this);
        getServer().getPluginManager().registerEvents(
                new ShopInteractionListener(this, repository, purchaseService), this);

        // Тикер истечений — async, но работает с блоками через runTask внутри
        long everySecond = 20L;
        getServer().getScheduler().runTaskTimerAsynchronously(
                this, () -> marketService.tickExpired(), everySecond, everySecond);

        long every5min = 20L * 60 * 5;
        getServer().getScheduler().runTaskTimerAsynchronously(
                this, () -> repository.save(), every5min, every5min);

        getLogger().info("RaskolMarket v" + getDescription().getVersion()
                + " включён. Регионов: " + repository.getRegions().size()
                + ", лавок: " + repository.getStalls().size());
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (repository != null) repository.save();
    }

    private Economy setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public MarketRepository getRepository() { return repository; }
    public MarketService getMarketService() { return marketService; }
    public PurchaseService getPurchaseService() { return purchaseService; }
}
