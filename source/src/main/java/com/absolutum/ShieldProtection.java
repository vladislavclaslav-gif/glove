package com.absolutum;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Абсолютная защита для того, кто держит Абсолютный щит (в любой руке).
 * Несколько слоёв — если какой-то мод обходит один, срабатывает следующий:
 * 1) отмена любой атаки (LivingAttackEvent) — до брони, тотемов и всего остального;
 * 2) отмена урона на поздних стадиях (LivingHurtEvent, LivingDamageEvent);
 * 3) отмена смерти (LivingDeathEvent) с полным восстановлением здоровья;
 * 4) каждый тик: здоровье на максимум, снятие негативных эффектов, тушение огня, воздух;
 * 5) без отбрасывания; при падении в бездну — телепорт на точку спавна.
 * Все обработчики с наивысшим приоритетом и получают даже уже отменённые события.
 */
@Mod.EventBusSubscriber(modid = Absolutum.MODID)
public final class ShieldProtection {

    private ShieldProtection() {}

    public static boolean isProtected(LivingEntity entity) {
        return entity.getMainHandItem().is(Absolutum.SHIELD.get())
                || entity.getOffhandItem().is(Absolutum.SHIELD.get());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (!isProtected(entity)) return;
        event.setCanceled(true);
        if (entity instanceof ServerPlayer player && event.getAmount() >= player.getHealth()) {
            ModAdvancements.award(player, "immortal");
        }
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) {
            rescueFromVoid(entity);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onHurt(LivingHurtEvent event) {
        if (isProtected(event.getEntity())) {
            event.setAmount(0.0F);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onDamage(LivingDamageEvent event) {
        if (isProtected(event.getEntity())) {
            event.setAmount(0.0F);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (isProtected(entity)) {
            event.setCanceled(true);
            restore(entity);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onKnockback(LivingKnockBackEvent event) {
        if (isProtected(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (isProtected(event.getEntity())
                && event.getEffectInstance().getEffect().getCategory() == MobEffectCategory.HARMFUL) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Срабатывает в начале каждого тика сущности — до того, как игра обработает смерть. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !isProtected(entity)) return;
        restore(entity);
    }

    private static void restore(LivingEntity entity) {
        if (entity.getHealth() < entity.getMaxHealth() || entity.isDeadOrDying()) {
            entity.setHealth(entity.getMaxHealth());
        }
        entity.deathTime = 0;
        entity.hurtTime = 0;
        if (entity.isOnFire()) entity.clearFire();
        entity.setTicksFrozen(0);
        entity.setAirSupply(entity.getMaxAirSupply());
        entity.fallDistance = 0.0F;

        List<MobEffectInstance> bad = new ArrayList<>();
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) bad.add(effect);
        }
        for (MobEffectInstance effect : bad) {
            entity.removeEffect(effect.getEffect());
        }
    }

    private static void rescueFromVoid(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (entity.getY() > level.getMinBuildHeight()) return;
        BlockPos spawn = level.getSharedSpawnPos();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        if (y <= level.getMinBuildHeight()) y = level.getSeaLevel() + 1;
        entity.setDeltaMovement(0, 0, 0);
        entity.teleportTo(spawn.getX() + 0.5, y + 1, spawn.getZ() + 0.5);
        entity.fallDistance = 0.0F;
        if (entity instanceof ServerPlayer player) {
            ModAdvancements.award(player, "void_walker");
        }
    }
}
