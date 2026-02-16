package ninja.trek.copperstring;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import ninja.trek.copperstring.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CopperStringSearch implements ModInitializer {
	public static final String MOD_ID = "copper-string-search";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static ModConfig config;

	public static ModConfig getConfig() {
		if (config == null) {
			config = ModConfig.load();
		}
		return config;
	}

	public static void setConfig(ModConfig newConfig) {
		config = newConfig;
	}

	@Override
	public void onInitialize() {
		config = ModConfig.load();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> ItemFilterCache.clearCache());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ItemFilterCache.clearCache());
	}
}
