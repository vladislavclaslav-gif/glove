package com.absolutum;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Убивает моба в несколько ступеней, каждая следующая — только если моб пережил предыдущую:
 * 1) бесконечный урон своим типом урона (в обход брони, неуязвимости, сопротивления, тотема);
 * 2) обнуление здоровья и смерть;  3) /kill;  4) удаление моба из мира (игроков — никогда).
 * Через 2 секунды — проверка: если моб "воскрес", он удаляется.
 */
@Mod.EventBusSubscriber(modid = Absolutum.MODID)
public final class Annihilator {

    private static final int RECHECK_TICKS = 40;
    private static final List<Pending> PENDING = new ArrayList<>();

    private Annihilator() {}

    /** Общая логика удара для перчатки и меча. Возвращает true, чтобы отменить обычную атаку. */
    public static boolean onLeftClick(Player player, Entity target) {
        Entity victim = target instanceof PartEntity<?> part ? part.getParent() : target;
        if (!(victim instanceof LivingEntity living)) return false;
        if (living instanceof Player p && (p.isCreative() || p.isSpectator())) return true;
        if (!player.level().isClientSide) annihilate(living, player);
        return true;
    }

    public static void annihilate(LivingEntity target, Player attacker) {
        if (!(target.level() instanceof ServerLevel level)) return;
        if (ShieldProtection.isProtected(target)) return; // Абсолютный щит сильнее

        DamageSource source = new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(Absolutum.ANNIHILATION),
                attacker);

        target.invulnerableTime = 0;
        target.hurt(source, Float.MAX_VALUE);

        if (target.isAlive()) {
            target.setHealth(0.0F);
            target.die(source);
        }
        if (target.isAlive()) {
            target.kill();
        }
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
                if (p.entity.isAlive() && !(p.entity instanceof Player)) {
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
