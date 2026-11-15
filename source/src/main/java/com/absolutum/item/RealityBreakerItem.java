package com.absolutum.item;

import com.absolutum.Absolutum;
import com.absolutum.ModAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Разрушитель Реальности. ЛКМ по любому блоку (даже бедроку, барьеру, порталу, командному блоку)
 * мгновенно убирает его и кладёт сам блок в инвентарь. Содержимое сундуков, спавнеров и т.п.
 * сохраняется внутри предмета — поставишь обратно, и всё будет на месте.
 */
@Mod.EventBusSubscriber(modid = Absolutum.MODID)
public class RealityBreakerItem extends Item {

    public RealityBreakerItem(Properties properties) { super(properties); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getItemStack().is(Absolutum.BREAKER.get())) return;
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) return;
        event.setCanceled(true); // обычное копание не нужно
        shatter(level, event.getPos(), event.getFace(), player);
    }

    private static void shatter(ServerLevel level, BlockPos pos, @Nullable Direction face, ServerPlayer player) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        // Какой предмет дать: как при "выборе блока" колёсиком мыши, иначе сам блок
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face == null ? Direction.UP : face, pos, false);
        ItemStack drop = ItemStack.EMPTY;
        try {
            drop = state.getCloneItemStack(hit, level, pos, player);
        } catch (Exception ignored) {
        }
        if (drop.isEmpty()) drop = new ItemStack(state.getBlock());

        // Сохраняем содержимое (сундук, спавнер и т.д.) внутрь предмета и убираем блок без выпадения вещей
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            if (!drop.isEmpty()) be.saveToItem(drop);
            level.removeBlockEntity(pos);
        }
        level.levelEvent(2001, pos, Block.getId(state)); // частицы и звук разрушения
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        if (!drop.isEmpty() && !player.getInventory().add(drop)) {
            player.drop(drop, false);
        }
        if (state.is(Blocks.BEDROCK)) {
            ModAdvancements.award(player, "bedrock_breaker");
        }
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Tooltips.add(stack, tooltip, ChatFormatting.DARK_RED);
        tooltip.add(Component.translatable("tooltip.absolutum.reality_breaker.hint").withStyle(ChatFormatting.GRAY));
    }
}
