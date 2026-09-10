package ru.raskol.market.model;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Stall {

    private final String key;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private String regionId;

    private UUID owner;       // null - лавка свободна
    private long expiresAt;   // конец аренды, ms

    private final Map<String, Double> prices = new HashMap<>(); // MATERIAL -> цена за штуку

    private UUID reclaimOwner;
    private long reclaimUntil;
    private final List<ItemStack> reclaimItems = new ArrayList<>();

    public Stall(String world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.key = world + ";" + x + ";" + y + ";" + z;
    }

    public static String keyOf(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";"
                + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public String getKey() { return key; }
    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public boolean isRented() {
        return owner != null && System.currentTimeMillis() < expiresAt;
    }

    public boolean isExpired() {
        return owner != null && System.currentTimeMillis() >= expiresAt;
    }

    public Map<String, Double> getPrices() { return prices; }

    public UUID getReclaimOwner() { return reclaimOwner; }
    public void setReclaimOwner(UUID reclaimOwner) { this.reclaimOwner = reclaimOwner; }
    public long getReclaimUntil() { return reclaimUntil; }
    public void setReclaimUntil(long reclaimUntil) { this.reclaimUntil = reclaimUntil; }
    public List<ItemStack> getReclaimItems() { return reclaimItems; }
}
