package ru.raskol.market;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.market.command.MarketCommand;
import ru.raskol.market.data.MarketRepository;
import ru.raskol.market.listener.MarketProtectionListener;
import ru.raskol.market.service.MarketService;

public final class RaskolMarket extends JavaPlugin {

    private MarketRepository repository;
    private MarketService service;
    private Economy economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("Vault/Economy не найдены — плагин отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = rsp.getProvider();

        repository = new MarketRepository(this);
        repository.load();

        service = new MarketService(this, repository, economy);

        PluginCommand cmd = getCommand("market");
        if (cmd != null) {
            MarketCommand executor = new MarketCommand(this, repository, service);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        getServer().getPluginManager().registerEvents(
                new MarketProtectionListener(this, repository), this);

        // Автосохранение каждые 5 минут
        long every5min = 20L * 60 * 5;
        getServer().getScheduler().runTaskTimerAsynchronously(
                this, repository::save, every5min, every5min);

        // Тикер истечений аренд каждые 30 секунд
        long every30s = 20L * 30;
        getServer().getScheduler().runTaskTimerAsynchronously(
                this, service::tickExpired, every30s, every30s);

        getLogger().info("RaskolMarket v" + getDescription().getVersion()
                + " включён. Регионов: " + repository.getRegions().size()
                + ", лавок: " + repository.getStalls().size());
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (repository != null) repository.save();
    }

    public MarketRepository getRepository() { return repository; }
    public MarketService getService() { return service; }
    public Economy getEconomy() { return economy; }
}
