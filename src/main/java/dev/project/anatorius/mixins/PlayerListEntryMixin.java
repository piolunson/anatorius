package dev.project.anatorius.mixins;

import com.mojang.authlib.GameProfile;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.core.CapeModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(PlayerListEntry.class)
public class PlayerListEntryMixin implements IMinecraft {
    @Shadow @Final private GameProfile profile;

    @Inject(method = "getSkinTextures", at = @At("TAIL"), cancellable = true)
    private void getSkinTextures(CallbackInfoReturnable<SkinTextures> info) {
        if (Anatorius.MODULE_MANAGER == null || profile == null) return;
        CapeModule irc = Anatorius.MODULE_MANAGER.getModule(CapeModule.class);
        if (irc == null || !irc.isToggled()) return;

        if (!isSelf()) return;

        SkinTextures textures = info.getReturnValue();
        if (textures == null) return;
        AssetInfo.TextureAsset cape = new AssetInfo.TextureAssetInfo(irc.getCapeTexture(), irc.getCapeTexture());
        info.setReturnValue(new SkinTextures(textures.body(), cape, textures.elytra(), textures.model(), textures.secure()));
    }

    private boolean isSelf() {
        if (mc.player != null) return Objects.equals(profile.id(), mc.player.getGameProfile().id());
        return mc.getSession() != null && Objects.equals(profile.id(), mc.getSession().getUuidOrNull());
    }
}
