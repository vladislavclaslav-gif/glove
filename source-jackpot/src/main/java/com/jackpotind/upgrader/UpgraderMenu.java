package com.jackpotind.upgrader;

import com.jackpotind.JackpotIndustries;
import com.jackpotind.ModAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Апгрейд-автомат. Слот 0 — ставка, слоты 1-3 — бонусы (любые предметы/блоки, добавляют ценность).
 * Два режима:
 *  • ЦЕЛЬ — выбираешь приз и количество, шанс = (ставка + бонусы) / ценность приза (максимум 95%);
 *  • ПРОЦЕНТ — сам ставишь шанс (1–95%), а автомат считает, сколько приза положено.
 *    Если приз не выбран — умножается твой же предмет из ставки.
 * При выигрыше иногда срабатывает СЕКРЕТНЫЙ АПГРЕЙД: приз умножается на ×2, ×3, ×5 или ×10.
 */
public class UpgraderMenu extends AbstractContainerMenu {
    public static final int SLOT_INPUT = 0, SLOT_BOOST = 1, BOOST_COUNT = 3, SIZE = 4;
    public static final int SPIN_TICKS = 70;
    public static final double MAX_CHANCE = 0.95;
    public static final int MAX_PRIZE = 4096;
    public static final int MODE_TARGET = 0, MODE_PERCENT = 1;

    /** Шанс секретного апгрейда при выигрыше. */
    public static final double SECRET_CHANCE = 0.10;
    /** Множители и их вес: ×2 — часто, ×10 — очень редко. */
    private static final int[] SECRET_MULT = {2, 3, 5, 10};
    private static final int[] SECRET_WEIGHT = {55, 25, 15, 5};

    public static final int BTN_SPIN = 0, BTN_MINUS = 1, BTN_PLUS = 2, BTN_MINUS10 = 3, BTN_PLUS10 = 4,
            BTN_MODE = 5, BTN_PCT_MINUS5 = 6, BTN_PCT_PLUS5 = 7, BTN_PCT_MINUS1 = 8, BTN_PCT_PLUS1 = 9, BTN_CLEAR_TARGET = 10;

    private static final int D_TARGET_LO = 0, D_TARGET_HI = 1, D_COUNT = 2, D_CHANCE = 3, D_ROLL = 4,
            D_SPIN_ID = 5, D_RESULT = 6, D_SPINNING = 7, D_MODE = 8, D_PERCENT = 9, D_SECRET = 10, D_PRIZE = 11;

    private final Player player;
    private final SimpleContainer container;
    private final ContainerData data = new SimpleContainerData(12);

    private final List<ItemStack> pending = new ArrayList<>();
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
        data.set(D_PERCENT, 50);
    }

    // ---------- чтение состояния (сервер и клиент) ----------
    /** Приз, выбранный в каталоге (может быть null). */
    @Nullable
    public Item chosenTarget() {
        int id = (data.get(D_TARGET_LO) & 0x7FFF) | (data.get(D_TARGET_HI) << 15);
        Item item = BuiltInRegistries.ITEM.byId(id);
        return item == Items.AIR ? null : item;
    }

    /** Что реально будет призом: выбранный предмет, а в режиме процента без выбора — сам предмет ставки. */
    @Nullable
    public Item target() {
        Item chosen = chosenTarget();
        if (chosen != null) return chosen;
        ItemStack in = container.getItem(SLOT_INPUT);
        return mode() == MODE_PERCENT && !in.isEmpty() ? in.getItem() : null;
    }

    public int mode() { return data.get(D_MODE); }
    public int percent() { return Math.max(1, data.get(D_PERCENT)); }
    public int count() { return Math.max(1, data.get(D_COUNT)); }
    /** Сколько штук приза положено (в режиме процента считается автоматически). */
    public int prize() { return data.get(D_PRIZE); }
    public double chance() { return data.get(D_CHANCE) / 10000.0; }
    public double roll() { return data.get(D_ROLL) / 10000.0; }
    public int spinId() { return data.get(D_SPIN_ID); }
    /** 0 — ещё не крутили, 1 — успех, 2 — провал. */
    public int result() { return data.get(D_RESULT); }
    /** Множитель секретного апгрейда последнего выигрыша (0 — не было). */
    public int secret() { return data.get(D_SECRET); }
    public boolean isSpinning() { return data.get(D_SPINNING) == 1; }
    public boolean hasStake() { return !container.getItem(SLOT_INPUT).isEmpty(); }

    public double stakeValue(Level level) {
        double sum = 0;
        for (int i = 0; i < SIZE; i++) sum += ItemValues.get(level, container.getItem(i));
        return sum;
    }

    public double targetValue(Level level) {
        Item t = target();
        return t == null ? 0 : ItemValues.get(level, t) * Math.max(1, prize());
    }

    // ---------- логика (сервер) ----------
    public void setTarget(@Nullable Item item) {
        if (isSpinning()) return;
        if (item != null && new ItemStack(item).is(ItemValues.BLACKLIST)) return;
        int id = item == null ? 0 : BuiltInRegistries.ITEM.getId(item);
        data.set(D_TARGET_LO, id & 0x7FFF);
        data.set(D_TARGET_HI, id >> 15);
        if (item != null) data.set(D_COUNT, Math.min(count(), new ItemStack(item).getMaxStackSize()));
        recompute();
    }

    private void recompute() {
        Level level = player.level();
        double chance = 0;
        int prize = 0;
        Item t = target();
        if (t != null && hasStake()) {
            double stake = stakeValue(level);
            double unit = ItemValues.get(level, t);
            if (mode() == MODE_PERCENT) {
                double p = percent() / 100.0;
                prize = (int) Math.min(MAX_PRIZE, Math.floor(stake / (p * unit)));
                // если упёрлись в лимит приза — шанс получается выше выбранного
                chance = prize < 1 ? 0 : Math.min(MAX_CHANCE, Math.max(p, stake / (prize * unit)));
            } else {
                prize = count();
                chance = Math.min(MAX_CHANCE, stake / (unit * prize));
            }
        }
        data.set(D_PRIZE, prize);
        data.set(D_CHANCE, (int) Math.round(chance * 10000));
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
            case BTN_MODE -> data.set(D_MODE, mode() == MODE_TARGET ? MODE_PERCENT : MODE_TARGET);
            case BTN_PCT_MINUS5 -> changePercent(-5);
            case BTN_PCT_PLUS5 -> changePercent(5);
            case BTN_PCT_MINUS1 -> changePercent(-1);
            case BTN_PCT_PLUS1 -> changePercent(1);
            case BTN_CLEAR_TARGET -> setTarget(null);
            default -> { return false; }
        }
        recompute();
        return true;
    }

    private void changeCount(int delta) {
        Item t = chosenTarget();
        int max = t == null ? 64 : new ItemStack(t).getMaxStackSize();
        data.set(D_COUNT, Math.max(1, Math.min(max, count() + delta)));
    }

    private void changePercent(int delta) {
        int v = percent() + delta;
        if (delta == 5 && percent() == 1) v = 5; // 1 → 5 → 10 ... красивые шаги
        data.set(D_PERCENT, Math.max(1, Math.min((int) Math.round(MAX_CHANCE * 100), v)));
    }

    private boolean spin() {
        recompute();
        Item t = target();
        int prize = prize();
        double chance = chance();
        if (t == null || !hasStake() || prize < 1 || chance <= 0) return false;

        double roll = player.getRandom().nextDouble();
        boolean success = roll < chance;
        int mult = 1;
        if (success && player.getRandom().nextDouble() < SECRET_CHANCE) mult = rollSecret();

        pending.clear();
        if (success) {
            int total = prize * mult;
            int max = new ItemStack(t).getMaxStackSize();
            while (total > 0) {
                int n = Math.min(max, total);
                pending.add(new ItemStack(t, n));
                total -= n;
            }
        }
        spinChance = chance;

        for (int i = 0; i < SIZE; i++) container.setItem(i, ItemStack.EMPTY); // ставка и бонусы сгорают
        data.set(D_PRIZE, prize);
        data.set(D_CHANCE, (int) Math.round(chance * 10000));
        data.set(D_ROLL, (int) Math.floor(roll * 10000));
        data.set(D_RESULT, success ? 1 : 2);
        data.set(D_SECRET, mult > 1 ? mult : 0);
        data.set(D_SPINNING, 1);
        data.set(D_SPIN_ID, (spinId() + 1) & 0x7FFF);
        spinEnd = player.level().getGameTime() + SPIN_TICKS;
        return true;
    }

    private int rollSecret() {
        int total = 0;
        for (int w : SECRET_WEIGHT) total += w;
        int r = player.getRandom().nextInt(total);
        for (int i = 0; i < SECRET_MULT.length; i++) {
            r -= SECRET_WEIGHT[i];
            if (r < 0) return SECRET_MULT[i];
        }
        return SECRET_MULT[0];
    }

    private void finishSpin() {
        data.set(D_SPINNING, 0);
        boolean success = result() == 1;
        for (int i = 0; i < pending.size(); i++) {
            ItemStack st = pending.get(i);
            if (i == 0) container.setItem(SLOT_INPUT, st);           // первая пачка — в слот ставки
            else player.getInventory().placeItemBackInInventory(st);  // остальное — в инвентарь (или под ноги)
        }
        pending.clear();
        if (player instanceof ServerPlayer sp) {
            if (success) {
                ModAdvancements.award(sp, "lucky");
                if (spinChance < 0.10) ModAdvancements.award(sp, "jackpot");
                if (secret() > 0) {
                    ModAdvancements.award(sp, "secret_upgrade");
                    if (secret() >= 10) ModAdvancements.award(sp, "secret_x10");
                    sp.sendSystemMessage(Component.translatable("msg.jackpotind.secret", secret())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                }
                if (mode() == MODE_PERCENT) ModAdvancements.award(sp, "risk_taker");
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
