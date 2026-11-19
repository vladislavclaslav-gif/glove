package com.jackpotind.drill;

import com.jackpotind.JackpotIndustries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashSet;
import java.util.Set;

/** Все настройки бура. Хранятся прямо в предмете, поэтому у каждого бура свои. */
public final class DrillSettings {
    public static final int MIN_LENGTH = 1, MAX_LENGTH = 128, MAX_SIZE = 9;

    /** Категории блоков. Списки — в data/jackpotind/tags/blocks/drill/*.json */
    public enum Category {
        DIRT("dirt"), STONE("stone"), SAND("sand"), PLANTS("plants"), WOOD("wood"),
        NETHER("nether"), END("end"), SNOW("snow"), ORES("ores");

        public final String key;
        public final TagKey<Block> tag;

        Category(String key) {
            this.key = key;
            this.tag = BlockTags.create(JackpotIndustries.id("drill/" + key));
        }
    }

    public enum Mode { DEPTH, SURFACE }
    public enum Drops { NONE, INVENTORY, GROUND }

    public Mode mode = Mode.DEPTH;
    public int length = 32, width = 3, height = 3;
    public Drops drops = Drops.NONE;
    public boolean allBlocks = false;
    public boolean protectContainers = true;
    public boolean removeFluids = false;
    public boolean sound = true;
    public int categories = bit(Category.DIRT) | bit(Category.STONE) | bit(Category.SAND) | bit(Category.PLANTS);
    public final Set<ResourceLocation> custom = new LinkedHashSet<>();

    public static int bit(Category c) { return 1 << c.ordinal(); }
    public boolean has(Category c) { return (categories & bit(c)) != 0; }
    public void toggle(Category c) { categories ^= bit(c); }

    public void clamp() {
        length = Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
        width = Math.max(1, Math.min(MAX_SIZE, width));
        height = Math.max(1, Math.min(MAX_SIZE, height));
        custom.removeIf(rl -> !BuiltInRegistries.BLOCK.containsKey(rl));
        while (custom.size() > 256) custom.remove(custom.iterator().next());
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("Mode", mode.ordinal());
        t.putInt("Length", length);
        t.putInt("Width", width);
        t.putInt("Height", height);
        t.putInt("Drops", drops.ordinal());
        t.putBoolean("All", allBlocks);
        t.putBoolean("Protect", protectContainers);
        t.putBoolean("Fluids", removeFluids);
        t.putBoolean("Sound", sound);
        t.putInt("Categories", categories);
        ListTag list = new ListTag();
        for (ResourceLocation rl : custom) list.add(StringTag.valueOf(rl.toString()));
        t.put("Custom", list);
        return t;
    }

    public static DrillSettings load(CompoundTag t) {
        DrillSettings s = new DrillSettings();
        if (t == null || t.isEmpty()) return s;
        s.mode = Mode.values()[Math.floorMod(t.getInt("Mode"), Mode.values().length)];
        s.length = t.getInt("Length");
        s.width = t.getInt("Width");
        s.height = t.getInt("Height");
        s.drops = Drops.values()[Math.floorMod(t.getInt("Drops"), Drops.values().length)];
        s.allBlocks = t.getBoolean("All");
        s.protectContainers = t.getBoolean("Protect");
        s.removeFluids = t.getBoolean("Fluids");
        s.sound = t.getBoolean("Sound");
        s.categories = t.getInt("Categories");
        for (Tag e : t.getList("Custom", Tag.TAG_STRING)) {
            ResourceLocation rl = ResourceLocation.tryParse(e.getAsString());
            if (rl != null) s.custom.add(rl);
        }
        s.clamp();
        return s;
    }

    public static DrillSettings of(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("Drill") ? load(stack.getTag().getCompound("Drill")) : new DrillSettings();
    }

    public void writeTo(ItemStack stack) {
        stack.getOrCreateTag().put("Drill", save());
    }
}
