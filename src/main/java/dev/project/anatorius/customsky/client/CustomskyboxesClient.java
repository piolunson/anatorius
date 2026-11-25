package dev.project.anatorius.customsky.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.util.Identifier;
import net.minecraft.resource.ResourceType;
import net.minecraft.resource.ResourceManager;
import dev.project.anatorius.customsky.Customskyboxes;
import dev.project.anatorius.customsky.client.command.SkyboxCommand;
import dev.project.anatorius.customsky.client.config.SkyboxConfig;
import dev.project.anatorius.customsky.client.network.ServerCommandPayload;
import dev.project.anatorius.customsky.client.network.ServerSync;
import dev.project.anatorius.customsky.client.resource.ResourcePackScanner;
import dev.project.anatorius.customsky.client.sky.SkyboxManager;
import dev.project.anatorius.customsky.client.texture.AnimatedTextureRegistry;
import dev.project.anatorius.customsky.network.SkyCommandPayload;
import dev.project.anatorius.customsky.network.SkyForgetPayload;

@Environment(EnvType.CLIENT)
public class CustomskyboxesClient implements ClientModInitializer {
   public void onInitializeClient() {
      ServerSync.bind((command) -> {
         if (ClientPlayNetworking.canSend(SkyForgetPayload.ID)) {
            ClientPlayNetworking.send(new SkyForgetPayload(command));
         }
      });
      Customskyboxes.LOGGER.info("CustomSkyboxes client init");
      ClientCommandRegistrationCallback.EVENT.register((ClientCommandRegistrationCallback)(dispatcher, registryAccess) -> SkyboxCommand.register(dispatcher));
      ClientPlayNetworking.registerGlobalReceiver(SkyCommandPayload.ID, (payload, context) -> context.client().execute(() -> ServerCommandPayload.run(context.client(), payload.command())));
      ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> {
         AnimatedTextureRegistry.get().tick();
         SkyboxManager.get().tickBiomeTransition();
      });
      ClientLifecycleEvents.CLIENT_STARTED.register((ClientLifecycleEvents.ClientStarted)(client) -> SkyboxConfig.load());
      ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
         private final Identifier ID = Customskyboxes.id("skybox_scanner");

         public Identifier getFabricId() {
            return this.ID;
         }

         public void reload(ResourceManager manager) {
            AnimatedTextureRegistry.get().reset();
            ResourcePackScanner.get().rescan();
         }
      });
   }
}
