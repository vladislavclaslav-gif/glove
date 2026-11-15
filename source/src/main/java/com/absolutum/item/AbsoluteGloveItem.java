package com.absolutum.item;

import com.absolutum.Annihilator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AbsoluteGloveItem extends Item {
    public AbsoluteGloveItem(Properties properties) { super(properties); }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        return Annihilator.onLeftClick(player, target);
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Tooltips.add(stack, tooltip, ChatFormatting.DARK_PURPLE);
    }
}
