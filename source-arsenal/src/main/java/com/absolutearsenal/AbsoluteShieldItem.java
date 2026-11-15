package com.absolutearsenal;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Щит без прочности (не ломается). Сама защита — в {@link ShieldProtection}. */
public class AbsoluteShieldItem extends ShieldItem {

    public AbsoluteShieldItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.absolutearsenal.absolute_shield").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.absolutearsenal.absolute_shield.hint").withStyle(ChatFormatting.GRAY));
    }
}
