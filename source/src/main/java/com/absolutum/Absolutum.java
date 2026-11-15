package com.absolutum;

import com.absolutum.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

@Mod(Absolutum.MODID)
public class Absolutum {
    public static final String MODID = "absolutum";

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    /** Свой тип урона: в обход брони, неуязвимости, эффектов, зачарований, щита и тотема. */
    public static final ResourceKey<DamageType> ANNIHILATION = ResourceKey.create(Registries.DAMAGE_TYPE, id("annihilation"));

    /** Блоки, которые ломает Бур Пустоты (земля, камень, песок). Список в data/absolutum/tags/blocks/drillable.json */
    public static final TagKey<Block> DRILLABLE = BlockTags.create(id("drillable"));

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    private static Item.Properties props() {
        return new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC);
    }

    private static RegistryObject<Item> item(String name, Supplier<Item> s) {
        return ITEMS.register(name, s);
    }

    public static final RegistryObject<Item> GLOVE   = item("absolute_glove",   () -> new AbsoluteGloveItem(props()));
    public static final RegistryObject<Item> SWORD   = item("absolute_sword",   () -> new AbsoluteSwordItem(props()));
    public static final RegistryObject<Item> SHIELD  = item("absolute_shield",  () -> new AbsoluteShieldItem(props()));
    public static final RegistryObject<Item> PICKAXE = item("absolute_pickaxe", () -> new AbsoluteTools.Pickaxe(props()));
    public static final RegistryObject<Item> AXE     = item("absolute_axe",     () -> new AbsoluteTools.Axe(props()));
    public static final RegistryObject<Item> SHOVEL  = item("absolute_shovel",  () -> new AbsoluteTools.Shovel(props()));
    public static final RegistryObject<Item> HOE     = item("absolute_hoe",     () -> new AbsoluteTools.Hoe(props()));
    public static final RegistryObject<Item> DRILL   = item("void_drill",       () -> new VoidDrillItem(props()));
    public static final RegistryObject<Item> BREAKER = item("reality_breaker",  () -> new RealityBreakerItem(props()));
    public static final RegistryObject<Item> CROWN   = item("absolute_crown",   () -> new AbsoluteCrownItem(props()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.absolutum"))
            .icon(() -> new ItemStack(SWORD.get()))
            .displayItems((params, output) -> ITEMS.getEntries().forEach(e -> output.accept(e.get())))
            .build());

    public Absolutum() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
