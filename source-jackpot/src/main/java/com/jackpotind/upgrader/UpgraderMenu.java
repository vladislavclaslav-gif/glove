package com.jackpotind.upgrader;

import com.jackpotind.JackpotIndustries;
import com.jackpotind.ModAdvancements;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Станция улучшения. Слот 0 — ставка, слоты 1-3 — бонусы (любые предметы/блоки, добавляют ценность).
 * Шанс = (ценность ставки + бонусов) / (ценность цели × количество), максимум 95%.
 * Ставка и бонусы сгорают в любом случае; при успехе в слот ставки приходит цель.
 */
public class UpgraderMenu extends AbstractContainerMenu {
    public static final int SLOT_INPUT = 0, SLOT_BOOST = 1, BOOST_COUNT = 3, SIZE = 4;
    public static final int SPIN_TICKS = 70;
    public static final double MAX_CHANCE = 0.95;
    public static final int BTN_SPIN = 0, BTN_MINUS = 1, BTN_PLUS = 2, BTN_MINUS10 = 3, BTN_PLUS10 = 4;

    private static final int D_TARGET_LO = 0, D_TARGET_HI = 1, D_COUNT = 2, D_CHANCE = 3, D_ROLL = 4,
            D_SPIN_ID = 5, D_RESULT = 6, D_SPINNING = 7;

    private final Player player;
    private final SimpleContainer container;
    private final ContainerData data = new SimpleContainerData(8);

    private ItemStack pending = ItemStack.EMPTY;
    private long spinEnd;
    private double spinChance;

    public UpgraderMenu(int id, Inventory inventory) {
        super(JackpotIndustries.UPGRADER_MENU.get(), id);
        this.player = inventory.player;
        this.container = new SimpleContainer(SIZE) {
            @Override
            public void setChanged() {
                super.setChanged();
                UpgraderMenu.this.slotsChanged(this);
            }
        };
        addSlot(new LockSlot(container, SLOT_INPUT, 22, 30));
        for (int i = 0; i < BOOST_COUNT; i++) addSlot(new LockSlot(container, SLOT_BOOST + i, 4 + i * 18, 64));
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 110 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 168));
        addDataSlots(data);
        data.set(D_COUNT, 1);
    }

    // ---------- чтение состояния (сервер и клиент) ----------
    @Nullable
    public Item target() {
        int id = (data.get(D_TARGET_LO) & 0x7FFF) | (data.get(D_TARGET_HI) << 15);
        Item item = BuiltInRegistries.ITEM.byId(id);
        return item == Items.AIR ? null : item;
    }

    public int count() { return Math.max(1, data.get(D_COUNT)); }
    public double chance() { return data.get(D_CHANCE) / 10000.0; }
    public double roll() { return data.get(D_ROLL) / 10000.0; }
    public int spinId() { return data.get(D_SPIN_ID); }
    /** 0 — ещё не крутили, 1 — успех, 2 — провал. */
    public int result() { return data.get(D_RESULT); }
    public boolean isSpinning() { return data.get(D_SPINNING) == 1; }

    public double stakeValue(Level level) {
        double sum = 0;
        for (int i = 0; i < SIZE; i++) sum += ItemValues.get(level, container.getItem(i));
        return sum;
    }

    public double targetValue(Level level) {
        Item t = target();
        return t == null ? 0 : ItemValues.get(level, t) * count();
    }

    // ---------- логика (сервер) ----------
    public void setTarget(Item item) {
        if (isSpinning() || new ItemStack(item).is(ItemValues.BLACKLIST)) return;
        int id = BuiltInRegistries.ITEM.getId(item);
        data.set(D_TARGET_LO, id & 0x7FFF);
        data.set(D_TARGET_HI, id >> 15);
        data.set(D_COUNT, Math.min(count(), new ItemStack(item).getMaxStackSize()));
        recompute();
    }

    private void recompute() {
        Level level = player.level();
        double c = 0;
        if (target() != null && !container.getItem(SLOT_INPUT).isEmpty()) {
            double need = targetValue(level);
            c = need <= 0 ? MAX_CHANCE : Math.min(MAX_CHANCE, stakeValue(level) / need);
        }
        data.set(D_CHANCE, (int) Math.round(c * 10000));
    }

    @Override
    public void slotsChanged(Container c) {
        super.slotsChanged(c);
        if (!player.level().isClientSide) recompute();
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (isSpinning()) return false;
        switch (id) {
            case BTN_SPIN -> { return spin(); }
            case BTN_MINUS -> changeCount(-1);
            case BTN_PLUS -> changeCount(1);
            case BTN_MINUS10 -> changeCount(-10);
            case BTN_PLUS10 -> changeCount(10);
            default -> { return false; }
        }
        return true;
    }

    private void changeCount(int delta) {
        Item t = target();
        int max = t == null ? 64 : new ItemStack(t).getMaxStackSize();
        data.set(D_COUNT, Math.max(1, Math.min(max, count() + delta)));
        recompute();
    }

    private boolean spin() {
        Item t = target();
        if (t == null || container.getItem(SLOT_INPUT).isEmpty()) return false;
        recompute();
        double chance = chance();
        if (chance <= 0) return false;

        double roll = player.getRandom().nextDouble();
        boolean success = roll < chance;
        pending = success ? new ItemStack(t, count()) : ItemStack.EMPTY;
        spinChance = chance;

        for (int i = 0; i < SIZE; i++) container.setItem(i, ItemStack.EMPTY); // ставка и бонусы сгорают
        data.set(D_CHANCE, (int) Math.round(chance * 10000));
        data.set(D_ROLL, (int) Math.floor(roll * 10000));
        data.set(D_RESULT, success ? 1 : 2);
        data.set(D_SPINNING, 1);
        data.set(D_SPIN_ID, (spinId() + 1) & 0x7FFF);
        spinEnd = player.level().getGameTime() + SPIN_TICKS;
        return true;
    }

    private void finishSpin() {
        data.set(D_SPINNING, 0);
        boolean success = result() == 1;
        if (!pending.isEmpty()) {
            container.setItem(SLOT_INPUT, pending);
            pending = ItemStack.EMPTY;
        }
        if (player instanceof ServerPlayer sp) {
            if (success) {
                ModAdvancements.award(sp, "lucky");
                if (spinChance < 0.10) ModAdvancements.award(sp, "jackpot");
            } else {
                ModAdvancements.award(sp, "unlucky");
            }
        }
    }

    /** Вызывается сервером каждый тик, пока меню открыто. */
    @Override
    public void broadcastChanges() {
        if (!player.level().isClientSide && isSpinning() && player.level().getGameTime() >= spinEnd) {
            finishSpin();
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(Player p) {
        if (!p.level().isClientSide) {
            if (isSpinning()) finishSpin(); // закрыл окно во время прокрутки — результат всё равно выдаём
            clearContainer(p, container);
        }
        super.removed(p);
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem() || isSpinning()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < SIZE) {
            if (!moveItemStackTo(stack, SIZE, SIZE + 36, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, SIZE, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(p, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player p) { return true; }

    /** Слот, который нельзя трогать во время прокрутки. */
    private class LockSlot extends Slot {
        LockSlot(Container c, int index, int x, int y) { super(c, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return !isSpinning(); }
        @Override public boolean mayPickup(Player p) { return !isSpinning(); }
    }
}
