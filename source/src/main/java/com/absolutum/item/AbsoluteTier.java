package com.absolutum.item;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/** Материал всех абсолютных предметов: прочность 0 = не ломаются, скорость копания огромная = всё ломается мгновенно. */
public final class AbsoluteTier implements Tier {
    public static final AbsoluteTier INSTANCE = new AbsoluteTier();
    private AbsoluteTier() {}

    @Override public int getUses() { return 0; }
    @Override public float getSpeed() { return 1_000_000.0F; }
    @Override public float getAttackDamageBonus() { return 4.0F; }
    @Override public int getLevel() { return 10; }
    @Override public int getEnchantmentValue() { return 30; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
}
