package ninja.trek.copperstring.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import ninja.trek.copperstring.CopperStringSearch;
import ninja.trek.copperstring.ItemFilterCache;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ModConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("copper-string-search.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, List<String>> aliases = new LinkedHashMap<>();

    public Map<String, List<String>> getAliases() {
        return aliases;
    }

    public List<String> getAlias(String name) {
        return aliases.get(name);
    }

    public void setAlias(String name, List<String> terms) {
        aliases.put(name, new ArrayList<>(terms));
    }

    public static ModConfig createDefault() {
        ModConfig config = new ModConfig();
        config.aliases.put("colors", List.of(
                "red", "orange", "yellow", "lime", "green", "cyan",
                "light_blue", "blue", "purple", "magenta", "pink",
                "white", "light_gray", "gray", "black", "brown"
        ));
        config.aliases.put("woods", List.of(
                "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
                "mangrove", "cherry", "bamboo", "crimson", "warped", "pale_oak"
        ));
        config.aliases.put("stones", List.of(
                "stone", "granite", "diorite", "andesite", "deepslate",
                "tuff", "calcite", "basalt", "blackstone"
        ));
        config.aliases.put("ores", List.of(
                "coal_ore", "iron_ore", "gold_ore", "diamond_ore",
                "emerald_ore", "lapis_ore", "redstone_ore", "copper_ore",
                "nether_gold_ore", "nether_quartz_ore"
        ));
        config.aliases.put("metals", List.of(
                "iron", "gold", "copper", "netherite"
        ));
        config.aliases.put("gems", List.of(
                "diamond", "emerald", "lapis", "amethyst", "quartz"
        ));
        config.aliases.put("crops", List.of(
                "wheat", "carrot", "potato", "beetroot", "melon",
                "pumpkin", "sugar_cane", "cocoa", "sweet_berry",
                "nether_wart", "cactus"
        ));
        config.aliases.put("tools", List.of(
                "pickaxe", "axe", "shovel", "hoe", "shears",
                "fishing_rod", "flint_and_steel"
        ));
        config.aliases.put("weapons", List.of(
                "sword", "bow", "crossbow", "trident", "mace"
        ));
        config.aliases.put("armor", List.of(
                "helmet", "chestplate", "leggings", "boots"
        ));
        config.aliases.put("food", List.of(
                "apple", "bread", "cooked", "stew", "soup", "cookie",
                "cake", "pie", "melon_slice", "dried_kelp", "baked_potato",
                "golden_apple", "golden_carrot", "rabbit", "mutton",
                "beef", "chicken", "porkchop", "cod", "salmon"
        ));
        config.aliases.put("music_discs", List.of(
                "music_disc"
        ));
        config.aliases.put("potions", List.of(
                "potion", "splash_potion", "lingering_potion"
        ));
        config.aliases.put("workstations", List.of(
                "crafting_table", "furnace", "blast_furnace", "smoker",
                "smithing_table", "fletching_table", "cartography_table",
                "brewing_stand", "enchanting_table", "anvil", "grindstone",
                "stonecutter", "loom", "composter", "barrel", "lectern"
        ));
        config.aliases.put("flowers", List.of(
                "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet",
                "tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley",
                "wither_rose", "torchflower", "pitcher_plant", "eyeblossom",
                "pink_petals", "wildflowers", "sunflower", "lilac",
                "rose_bush", "peony", "spore_blossom", "cactus_flower"
        ));
        config.aliases.put("plants", List.of(
                "grass", "fern", "bush", "seagrass", "sea_pickle",
                "lily_pad", "kelp", "vine", "glow_lichen", "moss",
                "dripleaf", "roots", "azalea", "mushroom", "fungus",
                "nether_sprouts", "sugar_cane", "cactus", "chorus_plant",
                "chorus_flower", "leaf_litter", "leaves"
        ));
        config.aliases.put("redstone", List.of(
                "redstone", "piston", "observer", "repeater", "comparator",
                "hopper", "dropper", "dispenser", "lever", "button",
                "pressure_plate", "tripwire"
        ));
        return config;
    }

    public static Map<String, List<String>> getDefaultAliases() {
        return createDefault().aliases;
    }

    public void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            CopperStringSearch.LOGGER.error("Failed to save config", e);
        }
        ItemFilterCache.clearCache();
    }

    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                if (config != null && config.aliases != null) {
                    // Merge with defaults to pick up new aliases from mod updates
                    ModConfig defaults = createDefault();
                    for (Map.Entry<String, List<String>> entry : defaults.aliases.entrySet()) {
                        config.aliases.putIfAbsent(entry.getKey(), entry.getValue());
                    }
                    return config;
                }
            } catch (Exception e) {
                CopperStringSearch.LOGGER.error("Failed to load config, using defaults", e);
            }
        }
        ModConfig config = createDefault();
        config.save();
        return config;
    }
}
