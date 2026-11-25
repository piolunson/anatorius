package dev.project.anatorius.customsky;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.customsky.client.CustomskyboxesClient;
import dev.project.anatorius.customsky.network.SkyCommandPayload;
import dev.project.anatorius.customsky.network.SkyForgetPayload;
import dev.project.anatorius.modules.impl.visuals.CustomSkyboxesModule;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class CustomSky {
    private CustomSky() {}

    public static void init() {
        if (Anatorius.MODULE_MANAGER == null || Anatorius.MODULE_MANAGER.getModule(CustomSkyboxesModule.class) == null) return;
        PayloadTypeRegistry.playS2C().register(SkyCommandPayload.ID, SkyCommandPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SkyForgetPayload.ID, SkyForgetPayload.CODEC);
        new CustomskyboxesClient().onInitializeClient();
    }

    public static boolean active() {
        if (Anatorius.MODULE_MANAGER == null) return false;
        CustomSkyboxesModule m = Anatorius.MODULE_MANAGER.getModule(CustomSkyboxesModule.class);
        return m != null && m.isToggled();
    }
}
