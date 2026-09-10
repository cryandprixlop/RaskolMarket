package ru.raskol.market.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ru.raskol.market.RaskolMarket;
import ru.raskol.market.model.MarketRegion;
import ru.raskol.market.model.Stall;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MarketRepository {

    private final RaskolMarket plugin;
    private final File file;
    private final Map<String, MarketRegion> regions = new LinkedHashMap<>();
    private final Map<String, Stall> stalls = new LinkedHashMap<>();

    public MarketRepository(RaskolMarket plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        regions.clear();
        stalls.clear();
        if (!file.exists()) {
            plugin.getLogger().info("data.yml не найден - создам при первом сохранении.");
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection rs = cfg.getConfigurationSection("regions");
        if (rs != null) {
            for (String id : rs.getKeys(false)) {
                ConfigurationSection s = rs.getConfigurationSection(id);
                if (s == null) continue;
                MarketRegion r = new MarketRegion(id,
                        s.getString("world", "world"),
                        s.getInt("min-x"), s.getInt("min-y"), s.getInt("min-z"),
                        s.getInt("max-x"), s.getInt("max-y"), s.getInt("max-z"));
                r.setTownName(s.getString("town", null));
                r.setRentPrice(s.getDouble("rent-price", -1));
                r.setRentSeconds(s.getLong("rent-seconds", -1));
                regions.put(id, r);
            }
        }

        ConfigurationSection ss = cfg.getConfigurationSection("stalls");
        if (ss != null) {
            for (String key : ss.getKeys(false)) {
                ConfigurationSection s = ss.getConfigurationSection(key);
                if (s == null) continue;
                String[] p = key.split(";");
                if (p.length != 4) continue;
                Stall st = new Stall(p[0],
                        Integer.parseInt(p[1]),
                        Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]));
                st.setRegionId(s.getString("region", null));
                String owner = s.getString("owner", "");
                if (owner != null && !owner.isBlank()) st.setOwner(UUID.fromString(owner));
                st.setExpiresAt(s.getLong("expires-at", 0));
                ConfigurationSection pr = s.getConfigurationSection("prices");
                if (pr != null) {
                    for (String mat : pr.getKeys(false)) {
                        st.getPrices().put(mat, pr.getDouble(mat));
                    }
                }
                String ro = s.getString("reclaim.owner", "");
                if (ro != null && !ro.isBlank()) st.setReclaimOwner(UUID.fromString(ro));
                st.setReclaimUntil(s.getLong("reclaim.until", 0));
                ConfigurationSection ri = s.getConfigurationSection("reclaim.items");
                if (ri != null) {
                    for (String i : ri.getKeys(false)) {
                        ItemStack it = ri.getItemStack(i);
                        if (it != null) st.getReclaimItems().add(it);
                    }
                }
                stalls.put(key, st);
            }
        }
        plugin.getLogger().info("RaskolMarket: загружено регионов=" + regions.size()
                + ", лавок=" + stalls.size());
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (MarketRegion r : regions.values()) {
            String b = "regions." + r.getId() + ".";
            cfg.set(b + "world", r.getWorld());
            cfg.set(b + "min-x", r.getMinX());
            cfg.set(b + "min-y", r.getMinY());
            cfg.set(b + "min-z", r.getMinZ());
            cfg.set(b + "max-x", r.getMaxX());
            cfg.set(b + "max-y", r.getMaxY());
            cfg.set(b + "max-z", r.getMaxZ());
            cfg.set(b + "town", r.getTownName());
            cfg.set(b + "rent-price", r.getRentPrice());
            cfg.set(b + "rent-seconds", r.getRentSeconds());
        }
        for (Stall st : stalls.values()) {
            String b = "stalls." + st.getKey() + ".";
            cfg.set(b + "region", st.getRegionId());
            cfg.set(b + "owner", st.getOwner() == null ? "" : st.getOwner().toString());
            cfg.set(b + "expires-at", st.getExpiresAt());
            for (Map.Entry<String, Double> e : st.getPrices().entrySet()) {
                cfg.set(b + "prices." + e.getKey(), e.getValue());
            }
            cfg.set(b + "reclaim.owner",
                    st.getReclaimOwner() == null ? "" : st.getReclaimOwner().toString());
            cfg.set(b + "reclaim.until", st.getReclaimUntil());
            List<ItemStack> items = st.getReclaimItems();
            for (int i = 0; i < items.size(); i++) {
                cfg.set(b + "reclaim.items." + i, items.get(i));
            }
        }
        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                plugin.getLogger().warning("Не удалось создать папку плагина.");
            }
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить data.yml: " + e.getMessage());
        }
    }

    /* ---------- регионы ---------- */
    public MarketRegion getRegion(String id) { return regions.get(id); }
    public Collection<MarketRegion> getRegions() { return regions.values(); }
    public void addRegion(MarketRegion r) { regions.put(r.getId(), r); }
    public void removeRegion(String id) {
        regions.remove(id);
        stalls.values().removeIf(st -> id.equals(st.getRegionId()));
    }
    public MarketRegion getRegionAt(org.bukkit.Location loc) {
        for (MarketRegion r : regions.values()) {
            if (r.contains(loc)) return r;
        }
        return null;
    }

    /* ---------- лавки ---------- */
    public Stall getStall(String key) { return stalls.get(key); }
    public Stall getStallAt(org.bukkit.Location loc) { return stalls.get(Stall.keyOf(loc)); }
    public Collection<Stall> getStalls() { return stalls.values(); }
    public void addStall(Stall st) { stalls.put(st.getKey(), st); }
    public void removeStall(String key) { stalls.remove(key); }
    public List<Stall> getStallsInRegion(String regionId) {
        List<Stall> out = new ArrayList<>();
        for (Stall st : stalls.values()) {
            if (regionId.equals(st.getRegionId())) out.add(st);
        }
        return out;
    }
    public int countRentedBy(UUID owner) {
        int n = 0;
        for (Stall st : stalls.values()) {
            if (st.isRented() && owner.equals(st.getOwner())) n++;
        }
        return n;
    }
}
