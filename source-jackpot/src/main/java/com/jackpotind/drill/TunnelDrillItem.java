package com.jackpotind.drill;

import com.jackpotind.ModAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Тоннельный бур. ПКМ — пробить тоннель по настройкам. Shift+ПКМ — окно настроек.
 * Блоки убираются без обновления соседей, чтобы не грузить компьютер.
 */
public class TunnelDrillItem extends Item {
    private static final int COOLDOWN = 4;

    public TunnelDrillItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (level.isClientSide) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.jackpotind.client.ClientHooks.openDrillSettings(hand));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            DrillSettings s = DrillSettings.of(stack);
            int removed = dig(server, sp, stack, s);
            if (removed > 0) {
                if (s.sound) level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 0.6F);
                ModAdvancements.award(sp, "tunnel");
                if (removed >= 1000) ModAdvancements.award(sp, "mega_tunnel");
            }
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // ---------------- копание ----------------
    private static int dig(ServerLevel level, ServerPlayer player, ItemStack tool, DrillSettings s) {
        Set<BlockPos> targets = new HashSet<>();
        Vec3 look = player.getLookAngle();
        int w0 = -(s.width - 1) / 2, w1 = s.width / 2;

        if (s.mode == DrillSettings.Mode.SURFACE) {
            Vec3 dir = new Vec3(look.x, 0, look.z);
            if (dir.lengthSqr() < 1.0E-4) dir = Vec3.directionFromRotation(0, player.getYRot());
            dir = dir.normalize();
            Vec3 right = new Vec3(-dir.z, 0, dir.x);
            Vec3 feet = player.position().add(0, 0.01, 0);
            for (double d = 0.5; d <= s.length; d += 0.5) {
                Vec3 c = feet.add(dir.scale(d));
                for (int i = w0; i <= w1; i++)
                    for (int j = 0; j < s.height; j++)
                        targets.add(BlockPos.containing(c.add(right.scale(i)).add(0, j, 0)));
            }
        } else {
            Vec3 horiz = new Vec3(look.x, 0, look.z);
            if (horiz.lengthSqr() < 1.0E-4) horiz = Vec3.directionFromRotation(0, player.getYRot());
            Vec3 right = new Vec3(-horiz.z, 0, horiz.x).normalize();
            Vec3 up = right.cross(look).normalize();
            if (up.y < 0) up = up.scale(-1);
            int h0 = -(s.height - 1) / 2, h1 = s.height / 2;
            Vec3 eye = player.getEyePosition();
            for (double d = 0.5; d <= s.length; d += 0.5) {
                Vec3 c = eye.add(look.scale(d));
                for (int i = w0; i <= w1; i++)
                    for (int j = h0; j <= h1; j++)
                        targets.add(BlockPos.containing(c.add(right.scale(i)).add(up.scale(j))));
            }
        }

        List<ItemStack> loot = new ArrayList<>();
        int removed = 0;
        for (BlockPos pos : targets) {
            if (!level.isLoaded(pos) || !level.isInWorldBounds(pos) || !level.mayInteract(player, pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !shouldBreak(level, pos, state, s)) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (s.drops != DrillSettings.Drops.NONE && !(state.getBlock() instanceof LiquidBlock)) {
                loot.addAll(Block.getDrops(state, level, pos, be, player, tool));
                if (be instanceof Container c) {
                    for (int i = 0; i < c.getContainerSize(); i++) if (!c.getItem(i).isEmpty()) loot.add(c.getItem(i).copy());
                }
            }
            Clearable.tryClear(be); // содержимое не высыпается само (или уже забрано в добычу)
            // "Убирать воду и лаву" выключено — на месте затопленных блоков остаётся жидкость, без пустот
            BlockState replacement = s.removeFluids ? Blocks.AIR.defaultBlockState() : state.getFluidState().createLegacyBlock();
            level.setBlock(pos, replacement, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            removed++;
        }

        for (ItemStack st : loot) {
            if (st.isEmpty()) continue;
            if (s.drops == DrillSettings.Drops.INVENTORY && player.getInventory().add(st)) continue;
            if (!st.isEmpty()) {
                if (s.drops == DrillSettings.Drops.GROUND) Block.popResource(level, player.blockPosition(), st);
                else player.drop(st, false);
            }
        }
        return removed;
    }

    private static boolean shouldBreak(ServerLevel level, BlockPos pos, BlockState state, DrillSettings s) {
        if (state.getDestroySpeed(level, pos) < 0) return false;          // бедрок, барьеры, порталы — никогда
        if (s.protectContainers && state.hasBlockEntity()) return false;   // сундуки, печи, механизмы
        if (state.getBlock() instanceof LiquidBlock) return s.removeFluids;
        if (s.allBlocks) return true;
        if (s.custom.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()))) return true;
        for (DrillSettings.Category c : DrillSettings.Category.values()) {
            if (s.has(c) && state.is(c.tag)) return true;
        }
        return false;
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        DrillSettings s = DrillSettings.of(stack);
        tooltip.add(Component.translatable("tooltip.jackpotind.tunnel_drill").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.jackpotind.tunnel_drill.mode",
                Component.translatable("drill.jackpotind.mode." + s.mode.name().toLowerCase())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.jackpotind.tunnel_drill.size", s.width, s.height, s.length).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.jackpotind.tunnel_drill.drops",
                Component.translatable("drill.jackpotind.drops." + s.drops.name().toLowerCase())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.jackpotind.tunnel_drill.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
