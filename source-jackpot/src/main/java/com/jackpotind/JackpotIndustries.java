package com.jackpotind;

import com.jackpotind.drill.DrillNetwork;
import com.jackpotind.drill.TunnelDrillItem;
import com.jackpotind.upgrader.JackpotMachineItem;
import com.jackpotind.upgrader.UpgraderMenu;
import com.jackpotind.upgrader.UpgraderNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(JackpotIndustries.MODID)
public class JackpotIndustries {
    public static final String MODID = "jackpotind";

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Item> JACKPOT_MACHINE = ITEMS.register("jackpot_machine",
            () -> new JackpotMachineItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> TUNNEL_DRILL = ITEMS.register("tunnel_drill",
            () -> new TunnelDrillItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)));

    public static final RegistryObject<MenuType<UpgraderMenu>> UPGRADER_MENU =
            MENUS.register("upgrader", () -> new MenuType<>(UpgraderMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.jackpotind"))
            .icon(() -> new ItemStack(JACKPOT_MACHINE.get()))
            .displayItems((params, out) -> ITEMS.getEntries().forEach(e -> out.accept(e.get())))
            .build());

    public JackpotIndustries() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        MENUS.register(modBus);
        TABS.register(modBus);
        UpgraderNetwork.register();
        DrillNetwork.register();
    }
}
