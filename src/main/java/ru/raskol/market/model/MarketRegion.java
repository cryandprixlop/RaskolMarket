package ru.raskol.market.model;

import org.bukkit.Location;

public final class MarketRegion {

    private final String id;
    private final String world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private String townName;
    private double rentPrice;   // <= 0 - брать из конфига
    private long rentSeconds;   // <= 0 - брать из конфига

    public MarketRegion(String id, String world,
                        int minX, int minY, int minZ,
                        int maxX, int maxY, int maxZ) {
        this.id = id;
        this.world = world;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public String getId() { return id; }
    public String getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    public String getTownName() { return townName; }
    public void setTownName(String townName) { this.townName = townName; }
    public double getRentPrice() { return rentPrice; }
    public void setRentPrice(double rentPrice) { this.rentPrice = rentPrice; }
    public long getRentSeconds() { return rentSeconds; }
    public void setRentSeconds(long rentSeconds) { this.rentSeconds = rentSeconds; }

    public boolean contains(String w, int x, int y, int z) {
        return this.world.equals(w)
                && x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public boolean contains(Location loc) {
        return loc.getWorld() != null
                && contains(loc.getWorld().getName(),
                            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
