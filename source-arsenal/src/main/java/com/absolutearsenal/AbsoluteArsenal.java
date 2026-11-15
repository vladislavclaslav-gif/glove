package com.absolutearsenal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(AbsoluteArsenal.MODID)
public class AbsoluteArsenal {
    public static final String MODID = "absolutearsenal";

    public static final ResourceKey<DamageType> ANNIHILATION =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(MODID, "annihilation"));

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Item> ABSOLUTE_SWORD = ITEMS.register("absolute_sword",
            () -> new AbsoluteSwordItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> ABSOLUTE_SHIELD = ITEMS.register("absolute_shield",
            () -> new AbsoluteShieldItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)));

    public AbsoluteArsenal() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::addToCreativeTab);
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ABSOLUTE_SWORD);
            event.accept(ABSOLUTE_SHIELD);
        }
    }
}
