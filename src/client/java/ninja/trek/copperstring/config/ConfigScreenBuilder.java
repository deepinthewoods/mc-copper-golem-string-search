package ninja.trek.copperstring.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ninja.trek.copperstring.CopperStringSearch;
import ninja.trek.copperstring.ItemFilterCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigScreenBuilder {

    public static Screen create(Screen parent) {
        ModConfig config = CopperStringSearch.getConfig();
        Map<String, List<String>> defaults = ModConfig.getDefaultAliases();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Copper String Search Config"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory aliasCategory = builder.getOrCreateCategory(
                Component.literal("Aliases"));

        for (Map.Entry<String, List<String>> entry : config.getAliases().entrySet()) {
            String aliasName = entry.getKey();
            List<String> defaultValue = defaults.getOrDefault(aliasName, List.of());

            aliasCategory.addEntry(entryBuilder.startStrList(
                            Component.literal("$" + aliasName),
                            new ArrayList<>(entry.getValue()))
                    .setDefaultValue(defaultValue)
                    .setTooltip(Component.literal("Terms that $" + aliasName + " expands to"))
                    .setSaveConsumer(newValue -> config.setAlias(aliasName, newValue))
                    .build());
        }

        builder.setSavingRunnable(() -> {
            config.save();
            ItemFilterCache.clearCache();
        });

        return builder.build();
    }
}
