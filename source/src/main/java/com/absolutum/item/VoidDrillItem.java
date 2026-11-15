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
 * Бур Пустоты. ПКМ — пробивает тоннель 3×3 на 32 блока. Shift+ПКМ — сменить режим:
 *  • «Вглубь» — по направлению взгляда (вверх, вниз, вбок — куда смотришь);
 *  • «По поверхности» — строго горизонтально на уровне игрока, сколько бы ты ни смотрел вниз/вверх.
 * Ломает землю, камень, песок и все растения (тег absolutum:drillable). Блоки просто исчезают:
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
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                boolean surface = !isSurfaceMode(stack);
                stack.getOrCreateTag().putBoolean(MODE_TAG, surface);
                player.displayClientMessage(Component.translatable("message.absolutum.void_drill.mode",
                        modeName(surface)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                if (player instanceof ServerPlayer sp) ModAdvancements.award(sp, "drill_mode");
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (level instanceof ServerLevel server) {
            int removed = isSurfaceMode(stack) ? drillSurface(server, player) : drill(server, player);
            if (removed > 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.5F);
                if (player instanceof ServerPlayer sp) ModAdvancements.award(sp, "tunnel");
            }
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static final String MODE_TAG = "SurfaceMode";

    public static boolean isSurfaceMode(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(MODE_TAG);
    }

    private static Component modeName(boolean surface) {
        return Component.translatable(surface ? "mode.absolutum.surface" : "mode.absolutum.depth");
    }

    /** Убирает блок, если он из списка; возвращает 1, если убрал. */
    private static int erase(ServerLevel level, Player player, BlockPos pos, Set<BlockPos> visited) {
        if (!visited.add(pos) || !level.isLoaded(pos)) return 0;
        BlockState state = level.getBlockState(pos);
        if (!state.is(Absolutum.DRILLABLE)) return 0;
        if (!level.mayInteract(player, pos)) return 0;
        // Подводные растения (водоросли, морская трава) заменяются водой, а не воздухом — без пустот под водой
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        return 1;
    }

    /** Режим «По поверхности»: горизонтальный тоннель 3 в ширину и 3 в высоту от уровня ног. */
    private static int drillSurface(ServerLevel level, Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1.0E-4) dir = Vec3.directionFromRotation(0, player.getYRot());
        dir = dir.normalize();
        Vec3 feet = player.position().add(0, 0.01, 0);
        Set<BlockPos> visited = new HashSet<>();
        int removed = 0;
        for (double d = 0.5; d <= RANGE; d += 0.5) {
            BlockPos center = BlockPos.containing(feet.add(dir.scale(d)));
            for (int dx = -RADIUS; dx <= RADIUS; dx++)
                for (int dz = -RADIUS; dz <= RADIUS; dz++)
                    for (int dy = 0; dy <= 2; dy++)
                        removed += erase(level, player, center.offset(dx, dy, dz), visited);
        }
        return removed;
    }

    /** Режим «Вглубь»: тоннель 3×3 по направлению взгляда. */
    private static int drill(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Set<BlockPos> visited = new HashSet<>();
        int removed = 0;

        for (double d = 0.5; d <= RANGE; d += 0.5) {
            BlockPos center = BlockPos.containing(eye.add(look.scale(d)));
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                    for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                        removed += erase(level, player, center.offset(dx, dy, dz), visited);
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
        tooltip.add(Component.translatable("tooltip.absolutum.void_drill.mode", modeName(isSurfaceMode(stack)))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.absolutum.void_drill.hint").withStyle(ChatFormatting.GRAY));
    }
}
