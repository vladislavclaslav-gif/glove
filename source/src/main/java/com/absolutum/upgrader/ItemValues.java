package com.absolutum.upgrader;

import com.absolutum.Absolutum;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * Ценность предметов (1 = один блок земли).
 * 1) Базовые значения для природных ресурсов и редкостей (таблица ниже).
 * 2) Всё, что крафтится или переплавляется, считается по рецептам: сумма ценности ингредиентов
 *    (берётся самый дешёвый рецепт). Поэтому работает и с предметами из других модов.
 * 3) Если ни того, ни другого нет — оценка по редкости предмета.
 * Считается одинаково на сервере и клиенте.
 */
public final class ItemValues {
    private ItemValues() {}

    /** Предметы, которые нельзя выбрать целью. data/absolutum/tags/items/upgrader_blacklist.json */
    public static final TagKey<Item> BLACKLIST = ItemTags.create(Absolutum.id("upgrader_blacklist"));
    private static final TagKey<Item> FORGE_ORES = ItemTags.create(new ResourceLocation("forge", "ores"));

    private static final Map<ResourceLocation, Double> BASE = new HashMap<>();
    private static void b(double v, String... ids) { for (String id : ids) BASE.put(new ResourceLocation(id), v); }
    static {
        b(0.5, "snowball", "wheat_seeds", "melon_slice", "bamboo", "kelp", "beetroot_seeds", "pumpkin_seeds", "melon_seeds");
        b(1, "dirt", "coarse_dirt", "grass_block", "podzol", "mycelium", "rooted_dirt", "mud", "sand", "red_sand", "gravel",
                "cobblestone", "cobbled_deepslate", "deepslate", "netherrack", "blackstone", "basalt", "soul_sand", "soul_soil",
                "tuff", "calcite", "dripstone_block", "pointed_dripstone", "rotten_flesh", "carrot", "potato", "beetroot",
                "sugar_cane", "cactus", "sweet_berries", "glow_berries", "seagrass", "vine", "lily_pad");
        b(2, "end_stone", "clay_ball", "ice", "bone", "string", "feather", "egg", "flint", "wheat", "moss_block",
                "brown_mushroom", "red_mushroom", "cocoa_beans", "chicken", "mutton", "dead_bush", "fern", "grass");
        b(3, "spider_eye", "ink_sac", "rabbit_hide", "apple", "pumpkin", "nether_wart", "beef", "porkchop", "rabbit",
                "cod", "salmon", "sculk", "crimson_fungus", "warped_fungus", "crimson_roots", "warped_roots");
        b(4, "gunpowder", "glowstone_dust", "chorus_fruit");
        b(5, "raw_copper", "leather", "prismarine_shard", "honeycomb", "tropical_fish", "pufferfish");
        b(6, "redstone", "amethyst_shard", "glow_ink_sac");
        b(8, "coal", "lapis_lazuli", "quartz", "prismarine_crystals");
        b(10, "slime_ball");
        b(15, "obsidian");
        b(18, "raw_iron");
        b(20, "crying_obsidian", "experience_bottle");
        b(27, "raw_gold");
        b(30, "ender_pearl", "phantom_membrane");
        b(40, "blaze_rod", "rabbit_foot");
        b(50, "scute", "turtle_egg");
        b(60, "emerald");
        b(80, "ghast_tear");
        b(100, "nautilus_shell", "name_tag", "sculk_sensor", "disc_fragment_5");
        b(150, "diamond");
        b(200, "saddle", "sponge", "wet_sponge", "dragon_breath", "sculk_catalyst");
        b(300, "echo_shard", "skeleton_skull", "zombie_head", "goat_horn", "sculk_shrieker");
        b(400, "ancient_debris", "shulker_shell", "creeper_head", "piglin_head");
        b(500, "player_head", "sniffer_egg", "netherite_upgrade_smithing_template");
        b(800, "totem_of_undying");
        b(1000, "wither_skeleton_skull", "budding_amethyst");
        b(1500, "heart_of_the_sea");
        b(2000, "trident");
        b(3000, "enchanted_golden_apple", "dragon_head");
        b(5000, "nether_star", "spawner", "reinforced_deepslate");
        b(6000, "elytra");
        b(10000, "bedrock", "end_portal_frame");
        b(20000, "dragon_egg");
    }

    private static final Map<RecipeManager, Cache> CACHES = new WeakHashMap<>();

    /** Ценность одного предмета (не меньше 0.1). */
    public static synchronized double get(Level level, Item item) {
        Cache cache = CACHES.computeIfAbsent(level.getRecipeManager(), m -> new Cache(m, level.registryAccess()));
        double v = cache.value(item, 0);
        return Double.isFinite(v) ? Math.max(0.1, v) : 1.0;
    }

    public static double get(Level level, ItemStack stack) {
        return stack.isEmpty() ? 0 : get(level, stack.getItem()) * stack.getCount();
    }

    public static String format(double v) {
        if (v >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", v / 1_000_000);
        if (v >= 10_000) return String.format(Locale.ROOT, "%.1fk", v / 1000);
        if (v >= 100) return String.valueOf(Math.round(v));
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static final class Cache {
        private final Map<Item, List<Recipe<?>>> byOutput = new HashMap<>();
        private final Map<Item, Double> memo = new HashMap<>();
        private final Set<Item> inProgress = new HashSet<>();
        private final RegistryAccess access;

        Cache(RecipeManager manager, RegistryAccess access) {
            this.access = access;
            for (Recipe<?> r : manager.getRecipes()) {
                ItemStack out;
                try { out = r.getResultItem(access); } catch (Exception e) { continue; }
                if (out != null && !out.isEmpty()) byOutput.computeIfAbsent(out.getItem(), k -> new ArrayList<>()).add(r);
            }
        }

        double value(Item item, int depth) {
            Double known = memo.get(item);
            if (known != null) return known;
            Double base = baseValue(item);
            if (base != null) { memo.put(item, base); return base; }
            if (depth > 16 || !inProgress.add(item)) return Double.POSITIVE_INFINITY;

            double best = Double.POSITIVE_INFINITY;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            // Незеритовые вещи делаются на кузнечном столе: алмазная вещь + незеритовый слиток
            if ("minecraft".equals(id.getNamespace()) && id.getPath().startsWith("netherite_")) {
                Item diamond = BuiltInRegistries.ITEM.get(new ResourceLocation("diamond_" + id.getPath().substring(10)));
                if (diamond != Items.AIR) best = value(diamond, depth + 1) + value(Items.NETHERITE_INGOT, depth + 1) + 20;
            }
            for (Recipe<?> r : byOutput.getOrDefault(item, List.of())) {
                double sum = 0;
                int used = 0;
                boolean ok = true;
                for (Ingredient ing : r.getIngredients()) {
                    if (ing.isEmpty()) continue;
                    double cheapest = Double.POSITIVE_INFINITY;
                    for (ItemStack option : ing.getItems()) {
                        cheapest = Math.min(cheapest, value(option.getItem(), depth + 1));
                    }
                    if (!Double.isFinite(cheapest)) { ok = false; break; }
                    sum += cheapest;
                    used++;
                }
                if (!ok || used == 0) continue;
                int count = Math.max(1, r.getResultItem(access).getCount());
                double v = sum / count + (r instanceof AbstractCookingRecipe ? 0.5 : 0);
                best = Math.min(best, v);
            }
            inProgress.remove(item);
            if (!Double.isFinite(best)) best = heuristic(item);
            memo.put(item, best);
            return best;
        }
    }

    private static Double baseValue(Item item) {
        Double v = BASE.get(BuiltInRegistries.ITEM.getKey(item));
        if (v != null) return v;
        ItemStack st = new ItemStack(item);
        if (st.is(ItemTags.LOGS)) return 4.0;
        if (st.is(ItemTags.LEAVES)) return 1.0;
        if (st.is(ItemTags.SAPLINGS) || st.is(ItemTags.FLOWERS)) return 2.0;
        if (st.is(ItemTags.MUSIC_DISCS)) return 500.0;
        if (st.is(ItemTags.TRIM_TEMPLATES)) return 200.0;
        if (st.is(FORGE_ORES)) return 40.0;
        return null;
    }

    private static double heuristic(Item item) {
        ItemStack st = new ItemStack(item);
        double v = switch (st.getRarity()) {
            case UNCOMMON -> 150;
            case RARE -> 600;
            case EPIC -> 3000;
            default -> 5;
        };
        if (item instanceof SpawnEggItem) v = 300;
        if (st.getMaxStackSize() == 1) v *= 4;
        if (st.isDamageableItem()) v += st.getMaxDamage() / 10.0;
        return v;
    }
}
