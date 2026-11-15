package com.absolutum;

import net.minecraft.advancements.Advancement;
import net.minecraft.server.level.ServerPlayer;

/** Выдаёт достижения, которые нельзя описать стандартными условиями (у них критерий "code"). */
public final class ModAdvancements {
    private ModAdvancements() {}

    public static void award(ServerPlayer player, String name) {
        Advancement adv = player.server.getAdvancements().getAdvancement(Absolutum.id(name));
        if (adv != null) {
            player.getAdvancements().award(adv, "code");
        }
    }
}
