package ninja.trek.copperstring;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import ninja.trek.copperstring.config.ModConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ItemFilterCache {

    public record FilterResult(
        Set<Item> includeItems,
        List<String> regularExcludeTerms,
        boolean hasRegularIncludes,
        boolean hasAnyIncludes,
        List<String> componentIncludeTerms,
        List<String> componentExcludeTerms
    ) {}

    private static final Map<String, FilterResult> CACHE = new ConcurrentHashMap<>();

    public static FilterResult getFilterResult(String name) {
        return CACHE.computeIfAbsent(name.toLowerCase(), ItemFilterCache::computeFilterResult);
    }

    public static Set<Item> getMatchingItems(String name) {
        return getFilterResult(name).includeItems();
    }

    private static FilterResult computeFilterResult(String name) {
        String[] tokens = name.split("\\s+");
        List<String> includeTerms = new ArrayList<>();
        List<String> excludeTerms = new ArrayList<>();
        List<String> componentIncludeTerms = new ArrayList<>();
        List<String> componentExcludeTerms = new ArrayList<>();

        ModConfig config = CopperStringSearch.getConfig();

        for (String token : tokens) {
            if (token.isEmpty()) continue;

            boolean exclude = token.startsWith("!");
            String term = exclude ? token.substring(1) : token;

            if (term.startsWith(".")) {
                String compTerm = term.substring(1);
                if (compTerm.isEmpty()) continue;
                if (exclude) {
                    componentExcludeTerms.add(compTerm);
                } else {
                    componentIncludeTerms.add(compTerm);
                }
            } else if (term.startsWith("$")) {
                String aliasName = term.substring(1);
                List<String> aliasTerms = config.getAlias(aliasName);
                if (aliasTerms != null) {
                    if (exclude) {
                        excludeTerms.addAll(aliasTerms);
                    } else {
                        includeTerms.addAll(aliasTerms);
                    }
                }
            } else if (!term.isEmpty()) {
                if (exclude) {
                    excludeTerms.add(term);
                } else {
                    includeTerms.add(term);
                }
            }
        }

        Set<Item> includeItems = new HashSet<>();
        boolean hasRegularIncludes = !includeTerms.isEmpty();

        if (!hasRegularIncludes && componentIncludeTerms.isEmpty()) {
            if (!excludeTerms.isEmpty() || !componentExcludeTerms.isEmpty()) {
                // Only exclusions: start with all items
                for (Item item : BuiltInRegistries.ITEM) {
                    includeItems.add(item);
                }
            }
        } else if (hasRegularIncludes) {
            // Collect items matching any regular include term
            for (Item item : BuiltInRegistries.ITEM) {
                String path = BuiltInRegistries.ITEM.getKey(item).getPath();
                for (String term : includeTerms) {
                    if (path.contains(term)) {
                        includeItems.add(item);
                        break;
                    }
                }
            }
        }

        boolean hasAnyIncludes = hasRegularIncludes || !componentIncludeTerms.isEmpty();

        return new FilterResult(
            includeItems,
            excludeTerms,
            hasRegularIncludes,
            hasAnyIncludes,
            componentIncludeTerms,
            componentExcludeTerms
        );
    }

    public static String getComponentSearchText(ItemStack stack) {
        StringBuilder sb = new StringBuilder();

        // Enchantments
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            appendEnchantments(sb, enchantments);
        }

        // Stored enchantments (enchanted books)
        ItemEnchantments storedEnchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (storedEnchantments != null) {
            appendEnchantments(sb, storedEnchantments);
        }

        // Lore
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                sb.append(line.getString()).append(' ');
            }
        }

        // Potion contents
        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null) {
            for (MobEffectInstance effect : potionContents.getAllEffects()) {
                sb.append(effect.getEffect().value().getDisplayName().getString()).append(' ');
            }
        }

        // Custom name
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            sb.append(customName.getString()).append(' ');
        }

        return sb.toString().toLowerCase();
    }

    private static void appendEnchantments(StringBuilder sb, ItemEnchantments enchantments) {
        for (var entry : enchantments.entrySet()) {
            sb.append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString()).append(' ');
        }
    }

    public static boolean stackMatchesFilter(FilterResult filter, ItemStack stack) {
        boolean included;
        String componentText = null;

        if (filter.hasAnyIncludes()) {
            included = filter.includeItems().contains(stack.getItem());

            if (!included && !filter.componentIncludeTerms().isEmpty()) {
                componentText = getComponentSearchText(stack);
                for (String term : filter.componentIncludeTerms()) {
                    if (componentText.contains(term)) {
                        included = true;
                        break;
                    }
                }
            }
        } else {
            included = true;
        }

        if (!included) return false;

        // Check regular excludes against registry path
        if (!filter.regularExcludeTerms().isEmpty()) {
            String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            for (String term : filter.regularExcludeTerms()) {
                if (path.contains(term)) {
                    return false;
                }
            }
        }

        // Check component excludes against tooltip text
        if (!filter.componentExcludeTerms().isEmpty()) {
            if (componentText == null) {
                componentText = getComponentSearchText(stack);
            }
            for (String term : filter.componentExcludeTerms()) {
                if (componentText.contains(term)) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean itemMatchesFilter(String name, Item item) {
        return getMatchingItems(name).contains(item);
    }

    /**
     * If the stack is a shulker box containing only one item type,
     * returns a single representative stack of that item type.
     * Otherwise returns the original stack unchanged.
     */
    public static ItemStack getEffectiveStack(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents == null) return stack;

            Item uniformItem = null;
            boolean hasItems = false;
            for (ItemStack contained : contents.nonEmptyItems()) {
                hasItems = true;
                if (uniformItem == null) {
                    uniformItem = contained.getItem();
                } else if (uniformItem != contained.getItem()) {
                    return stack; // mixed contents
                }
            }
            if (hasItems) {
                return new ItemStack(uniformItem, 1);
            }
        }
        return stack;
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
