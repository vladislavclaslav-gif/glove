package com.absoluteglove;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Убивает моба в несколько ступеней. Каждая следующая срабатывает, только если моб пережил предыдущую:
 * 1) бесконечный урон своим типом урона (в обход брони, неуязвимости, сопротивления, тотема);
 * 2) принудительное обнуление здоровья и смерть;
 * 3) /kill;
 * 4) удаление сущности из мира.
 * Плюс через 2 секунды проверка: если моб каким-то образом "воскрес" — он удаляется.
 */
@Mod.EventBusSubscriber(modid = AbsoluteGlove.MODID)
public final class Annihilator {

    private static final int RECHECK_TICKS = 40;
    private static final List<Pending> PENDING = new ArrayList<>();

    private Annihilator() {}

    public static void annihilate(LivingEntity target, Player attacker) {
        if (!(target.level() instanceof ServerLevel level)) return;
        

        DamageSource source = new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(AbsoluteGlove.ANNIHILATION),
                attacker);

        // 1. Бесконечный урон
        target.invulnerableTime = 0;
        target.hurt(source, Float.MAX_VALUE);

        // 2. Обнуляем здоровье напрямую
        if (target.isAlive()) {
            target.setHealth(0.0F);
            target.die(source);
        }

        // 3. Ванильный /kill
        if (target.isAlive()) {
            target.kill();
        }

        // 4. Если моб из мода всё равно жив — просто стираем его из мира
        // (игроков из мира не стираем — это ломает сервер)
        if (target.isAlive() && !(target instanceof Player)) {
            target.remove(Entity.RemovalReason.KILLED);
            return;
        }

        PENDING.add(new Pending(target, RECHECK_TICKS));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.entity.isRemoved()) { it.remove(); continue; }
            if (--p.ticksLeft <= 0) {
                if (p.entity.isAlive() && !(p.entity instanceof Player)) { // "воскрес" — удаляем окончательно
                    p.entity.remove(Entity.RemovalReason.KILLED);
                }
                it.remove();
            }
        }
    }

    private static final class Pending {
        final LivingEntity entity;
        int ticksLeft;
        Pending(LivingEntity entity, int ticksLeft) { this.entity = entity; this.ticksLeft = ticksLeft; }
    }
}
