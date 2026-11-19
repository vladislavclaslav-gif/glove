package com.jackpotind.drill;

import com.jackpotind.JackpotIndustries;
import com.jackpotind.ModAdvancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Клиент -> сервер: сохранить настройки бура в руке. */
public final class DrillNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            JackpotIndustries.id("drill"), () -> VERSION, VERSION::equals, VERSION::equals);

    private DrillNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(Save.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Save::encode).decoder(Save::decode).consumerMainThread(Save::handle).add();
    }

    public record Save(boolean mainHand, CompoundTag settings) {
        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(mainHand);
            buf.writeNbt(settings);
        }

        static Save decode(FriendlyByteBuf buf) {
            return new Save(buf.readBoolean(), buf.readNbt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || settings == null) return;
            ItemStack stack = player.getItemInHand(mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            if (!stack.is(JackpotIndustries.TUNNEL_DRILL.get())) return;
            DrillSettings s = DrillSettings.load(settings); // load() сам проверяет и ограничивает значения
            s.writeTo(stack);
            ModAdvancements.award(player, "drill_tuner");
        }
    }
}
