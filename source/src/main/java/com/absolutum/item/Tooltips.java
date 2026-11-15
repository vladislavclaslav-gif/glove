package com.absolutum.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

final class Tooltips {
    private Tooltips() {}

    /** Добавляет подсказку "tooltip.absolutum.<имя_предмета>". */
    static void add(ItemStack stack, List<Component> tooltip, ChatFormatting color) {
        Item item = stack.getItem();
        var key = ForgeRegistries.ITEMS.getKey(item);
        if (key != null) {
            tooltip.add(Component.translatable("tooltip.absolutum." + key.getPath()).withStyle(color));
        }
    }
}
