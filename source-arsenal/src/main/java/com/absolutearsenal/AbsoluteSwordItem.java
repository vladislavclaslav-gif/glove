package com.absolutearsenal;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AbsoluteSwordItem extends SwordItem {

    /** Материал меча: прочность 0 = меч неломаемый. */
    private static final Tier ABSOLUTE = new Tier() {
        @Override public int getUses() { return 0; }
        @Override public float getSpeed() { return 20.0F; }
        @Override public float getAttackDamageBonus() { return 0.0F; }
        @Override public int getLevel() { return 5; }
        @Override public int getEnchantmentValue() { return 30; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };

    public AbsoluteSwordItem(Properties properties) {
        super(ABSOLUTE, 999, 10.0F, properties);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        Entity victim = target instanceof PartEntity<?> part ? part.getParent() : target;
        if (!(victim instanceof LivingEntity living)) {
            return false;
        }
        if (living instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return true;
        }
        if (!player.level().isClientSide) {
            Annihilator.annihilate(living, player);
        }
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.absolutearsenal.absolute_sword").withStyle(ChatFormatting.GOLD));
    }
}
