package ru.raskol.market;

import org.bukkit.plugin.java.JavaPlugin;
import ru.raskol.market.data.MarketRepository;

public final class RaskolMarket extends JavaPlugin {

    private MarketRepository repository;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        repository = new MarketRepository(this);
        repository.load();

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

    public MarketRepository getRepository() {
        return repository;
    }
}
