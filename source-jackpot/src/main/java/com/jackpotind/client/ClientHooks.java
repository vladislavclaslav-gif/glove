package com.jackpotind.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

public final class ClientHooks {
    private ClientHooks() {}

    public static void openDrillSettings(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setScreen(new DrillSettingsScreen(hand, mc.player.getItemInHand(hand)));
    }
}
