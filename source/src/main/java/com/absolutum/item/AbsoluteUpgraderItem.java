package com.absolutum.item;

import com.absolutum.upgrader.UpgraderMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Абсолютный Апгрейдер: ПКМ — открыть станцию улучшения. */
public class AbsoluteUpgraderItem extends Item {
    public AbsoluteUpgraderItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new UpgraderMenu(id, inv),
                    Component.translatable("menu.absolutum.upgrader")));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Tooltips.add(stack, tooltip, ChatFormatting.GREEN);
        tooltip.add(Component.translatable("tooltip.absolutum.absolute_upgrader.hint").withStyle(ChatFormatting.GRAY));
    }
}
