package com.absolutum.item;

import com.absolutum.Absolutum;
import com.absolutum.ModAdvancements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Корона Абсолюта (шлем). Пока надета — все полезные эффекты на максимальном уровне.
 * Уровни у скорости, прыжка, спешки, поглощения и прибавки здоровья ограничены:
 * при уровне 255 игра ломается (нельзя управлять, сердца закрывают экран).
 */
@Mod.EventBusSubscriber(modid = Absolutum.MODID)
public class AbsoluteCrownItem extends ArmorItem {

    /** Эффект -> уровень (0 = I, 4 = V, 255 = максимум игры). */
    public static final Map<MobEffect, Integer> EFFECTS = new LinkedHashMap<>();
    static {
        EFFECTS.put(MobEffects.REGENERATION, 255);
        EFFECTS.put(MobEffects.DAMAGE_BOOST, 255);       // Сила
        EFFECTS.put(MobEffects.DAMAGE_RESISTANCE, 4);    // Сопротивление V = полная неуязвимость
        EFFECTS.put(MobEffects.FIRE_RESISTANCE, 0);
        EFFECTS.put(MobEffects.WATER_BREATHING, 0);
        EFFECTS.put(MobEffects.NIGHT_VISION, 0);
        EFFECTS.put(MobEffects.SATURATION, 255);
        EFFECTS.put(MobEffects.LUCK, 255);
        EFFECTS.put(MobEffects.HERO_OF_THE_VILLAGE, 255);
        EFFECTS.put(MobEffects.CONDUIT_POWER, 0);
        EFFECTS.put(MobEffects.DOLPHINS_GRACE, 0);
        EFFECTS.put(MobEffects.MOVEMENT_SPEED, 4);       // Скорость V
        EFFECTS.put(MobEffects.DIG_SPEED, 4);            // Спешка V
        EFFECTS.put(MobEffects.JUMP, 2);                 // Прыгучесть III
        EFFECTS.put(MobEffects.ABSORPTION, 4);           // +20 жёлтых сердец
        EFFECTS.put(MobEffects.HEALTH_BOOST, 4);         // +10 сердец к максимуму
    }

    /** Бессрочные эффекты: накладываются один раз и не обновляются (иначе "Прибавка здоровья" при обновлении срезала здоровье). */
    private static final int DURATION = MobEffectInstance.INFINITE_DURATION;

    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override public int getDurabilityForType(Type type) { return 0; } // неломаемая
        @Override public int getDefenseForType(Type type) { return 10; }
        @Override public int getEnchantmentValue() { return 30; }
        @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_NETHERITE; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
        @Override public String getName() { return Absolutum.MODID + ":absolute"; }
        @Override public float getToughness() { return 10.0F; }
        @Override public float getKnockbackResistance() { return 1.0F; }
    };

    public AbsoluteCrownItem(Properties properties) {
        super(MATERIAL, Type.HELMET, properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        if (player.getItemBySlot(EquipmentSlot.HEAD) != stack) return; // работает только надетой

        if (player.tickCount % 20 == 0) {
            for (Map.Entry<MobEffect, Integer> e : EFFECTS.entrySet()) {
                MobEffectInstance cur = player.getEffect(e.getKey());
                if (cur == null || cur.getAmplifier() < e.getValue() || !cur.isInfiniteDuration()) {
                    player.addEffect(new MobEffectInstance(e.getKey(), DURATION, e.getValue(), true, false, true));
                }
            }
        }
        if (player instanceof ServerPlayer sp
                && (sp.getMainHandItem().is(Absolutum.SWORD.get()) || sp.getMainHandItem().is(Absolutum.GLOVE.get()))
                && sp.getOffhandItem().is(Absolutum.SHIELD.get())) {
            ModAdvancements.award(sp, "godlike");
        }
    }

    /** Сняли корону — эффекты пропадают сразу. */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getSlot() != EquipmentSlot.HEAD) return;
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;
        if (event.getFrom().is(Absolutum.CROWN.get()) && !event.getTo().is(Absolutum.CROWN.get())) {
            for (MobEffect effect : EFFECTS.keySet()) {
                MobEffectInstance cur = entity.getEffect(effect);
                if (cur != null && cur.isAmbient()) entity.removeEffect(effect);
            }
            if (entity.getHealth() > entity.getMaxHealth()) entity.setHealth(entity.getMaxHealth());
        }
    }

    @Override public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Tooltips.add(stack, tooltip, ChatFormatting.GOLD);
        tooltip.add(Component.translatable("tooltip.absolutum.absolute_crown.hint").withStyle(ChatFormatting.GRAY));
    }
}
