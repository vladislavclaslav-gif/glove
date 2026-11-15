package com.absolutum.upgrader;

import com.absolutum.Absolutum;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/** Пакет "игрок выбрал цель в каталоге". Остальные кнопки идут через стандартные кнопки меню. */
public final class UpgraderNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Absolutum.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private UpgraderNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(SetTarget.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetTarget::encode)
                .decoder(SetTarget::decode)
                .consumerMainThread(SetTarget::handle)
                .add();
    }

    public record SetTarget(int containerId, ResourceLocation item) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(containerId);
            buf.writeResourceLocation(item);
        }

        static SetTarget decode(FriendlyByteBuf buf) {
            return new SetTarget(buf.readVarInt(), buf.readResourceLocation());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof UpgraderMenu menu && menu.containerId == containerId) {
                Item target = ForgeRegistries.ITEMS.getValue(item);
                if (target != null && target != Items.AIR) menu.setTarget(target);
            }
        }
    }
}
