package io.github.tootertutor.ModularPacks.modules.quiver;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

/** Mutable lifecycle state for one temporary vanilla ammunition proxy. */
public final class QuiverAmmoLease {

    public enum State {
        MARKED,
        READY,
        COMMITTED,
        CLEANED
    }

    private final UUID leaseId;
    private final UUID playerId;
    private final QuiverSource source;
    private final int inventorySlot;
    private final Material weaponType;
    private final EquipmentSlot hand;
    private final int createdTick;
    private State state = State.MARKED;
    private boolean vanillaConsumptionExpected;
    private int amountBeforeVanillaConsumption = 1;

    QuiverAmmoLease(UUID leaseId, UUID playerId, QuiverSource source, int inventorySlot,
            Material weaponType, EquipmentSlot hand, int createdTick) {
        this.leaseId = leaseId;
        this.playerId = playerId;
        this.source = source;
        this.inventorySlot = inventorySlot;
        this.weaponType = weaponType;
        this.hand = hand;
        this.createdTick = createdTick;
    }

    public UUID leaseId() {
        return leaseId;
    }

    public UUID playerId() {
        return playerId;
    }

    public QuiverSource source() {
        return source;
    }

    public int inventorySlot() {
        return inventorySlot;
    }

    public Material weaponType() {
        return weaponType;
    }

    public EquipmentSlot hand() {
        return hand;
    }

    public int createdTick() {
        return createdTick;
    }

    public State state() {
        return state;
    }

    public boolean ready() {
        return state == State.READY || state == State.COMMITTED;
    }

    public boolean cleaned() {
        return state == State.CLEANED;
    }

    void markReady() {
        if (state == State.MARKED) {
            state = State.READY;
        }
    }

    void markCommitted(boolean consumptionExpected, int amountBeforeConsumption) {
        vanillaConsumptionExpected = consumptionExpected;
        amountBeforeVanillaConsumption = Math.max(1, amountBeforeConsumption);
        state = State.COMMITTED;
    }

    boolean vanillaConsumptionExpected() {
        return vanillaConsumptionExpected;
    }

    int amountBeforeVanillaConsumption() {
        return amountBeforeVanillaConsumption;
    }

    void markCleaned() {
        state = State.CLEANED;
    }
}
