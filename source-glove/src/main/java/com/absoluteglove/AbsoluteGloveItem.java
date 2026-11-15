package com.absoluteglove;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AbsoluteGloveItem extends Item {

    public AbsoluteGloveItem(Properties properties) {
        super(properties);
    }

    /** Вызывается при ударе ЛКМ по сущности (на клиенте и на сервере). true = отменить обычную атаку. */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        // У многосоставных мобов (дракон Края и боссы из модов) бьём по "главному" телу
        Entity victim = target instanceof PartEntity<?> part ? part.getParent() : target;

        if (!(victim instanceof LivingEntity living)) {
            return false; // не моб (лодка, рамка и т.п.) — обычный удар
        }
        if (living instanceof Player p && (p.isCreative() || p.isSpectator())) {
            return true; // игроков в креативе/наблюдателе не трогаем
        }
        if (!player.level().isClientSide) {
            Annihilator.annihilate(living, player);
        }
        return true;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true; // всегда светится, как зачарованная
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.absoluteglove.absolute_glove").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
