package ru.raskol.market.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** Маркер GUI-витрины: хранит ключ лавки и соответствие слот -> материал. */
public final class StallGuiHolder implements InventoryHolder {

    private final String stallKey;
    private final Map<Integer, String> slotMaterial = new HashMap<>();
    private Inventory inventory;

    public StallGuiHolder(String stallKey) {
        this.stallKey = stallKey;
    }

    public String getStallKey() { return stallKey; }
    public Map<Integer, String> getSlotMaterial() { return slotMaterial; }
    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
