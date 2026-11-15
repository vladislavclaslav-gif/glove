package com.absolutum.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Абсолютные инструменты. Мгновенно ломают любой блок, подходящий своему инструменту
 * (кирка — всё, что ломается киркой, и т.д.), включая блоки из других модов. Ресурсы выпадают как обычно.
 */
public final class AbsoluteTools {
    private AbsoluteTools() {}

    public static class Pickaxe extends PickaxeItem {
        public Pickaxe(Properties p) { super(AbsoluteTier.INSTANCE, 4, -2.0F, p); }
        @Override public boolean isFoil(ItemStack s) { return true; }
        @Override public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) { Tooltips.add(s, t, ChatFormatting.AQUA); }
    }

    public static class Axe extends AxeItem {
        public Axe(Properties p) { super(AbsoluteTier.INSTANCE, 10.0F, -2.0F, p); }
        @Override public boolean isFoil(ItemStack s) { return true; }
        @Override public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) { Tooltips.add(s, t, ChatFormatting.AQUA); }
    }

    public static class Shovel extends ShovelItem {
        public Shovel(Properties p) { super(AbsoluteTier.INSTANCE, 3.0F, -2.0F, p); }
        @Override public boolean isFoil(ItemStack s) { return true; }
        @Override public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) { Tooltips.add(s, t, ChatFormatting.AQUA); }
    }

    public static class Hoe extends HoeItem {
        public Hoe(Properties p) { super(AbsoluteTier.INSTANCE, 0, 0.0F, p); }
        @Override public boolean isFoil(ItemStack s) { return true; }
        @Override public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) { Tooltips.add(s, t, ChatFormatting.AQUA); }
    }
}
