package com.jackpotind.upgrader;

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

/** Апгрейд-автомат «Джекпот»: ПКМ — открыть. */
public class JackpotMachineItem extends Item {
    public JackpotMachineItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new UpgraderMenu(id, inv),
                    Component.translatable("menu.jackpotind.upgrader")));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.jackpotind.jackpot_machine").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.jackpotind.jackpot_machine.hint").withStyle(ChatFormatting.GRAY));
    }
}
