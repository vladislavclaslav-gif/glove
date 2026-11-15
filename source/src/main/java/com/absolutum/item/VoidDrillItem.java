package com.absolutum.item;

import com.absolutum.Absolutum;
import com.absolutum.ModAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Бур Пустоты. ПКМ — пробивает тоннель 3×3 на 32 блока в направлении взгляда.
 * Ломает только землю, камень и песок (тег absolutum:drillable). Блоки просто исчезают:
 * ничего не выпадает и соседние блоки не обновляются — чтобы не нагружать компьютер.
 */
public class VoidDrillItem extends Item {
    public static final int RANGE = 32;
    private static final int RADIUS = 1;      // 1 = тоннель 3×3
    private static final int COOLDOWN = 4;    // тиков между использованиями

    public VoidDrillItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            int removed = drill(server, player);
            if (removed > 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.5F);
                if (player instanceof ServerPlayer sp) ModAdvancements.award(sp, "tunnel");
            }
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static int drill(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Set<BlockPos> visited = new HashSet<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        int removed = 0;

        for (double d = 0.5; d <= RANGE; d += 0.5) {
            BlockPos center = BlockPos.containing(eye.add(look.scale(d)));
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                    for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        if (!visited.add(pos) || !level.isLoaded(pos)) continue;
                        if (!level.getBlockState(pos).is(Absolutum.DRILLABLE)) continue;
                        if (!level.mayInteract(player, pos)) continue; // уважаем защиту спавна/приватов
                        // Только обновление у клиентов: без дропа, без обновления соседей
                        level.setBlock(pos, air, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Tooltips.add(stack, tooltip, ChatFormatting.LIGHT_PURPLE);
        tooltip.add(Component.translatable("tooltip.absolutum.void_drill.hint").withStyle(ChatFormatting.GRAY));
    }
}
