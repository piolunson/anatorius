package dev.project.anatorius.modules.impl.miscellaneous;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.utils.system.ClipboardImage;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;

import java.io.File;
import java.util.concurrent.CompletableFuture;

@RegisterModule(name = "ExtraScreenshot", description = "Screenshots (F2) are saved to the screenshots folder and copied to your clipboard.", category = Module.Category.MISCELLANEOUS)
public class ExtraScreenshotModule extends Module {
    public static volatile Boolean lastCopied;

    public static void onScreenshotMessage(Text message) {
        if (Anatorius.MODULE_MANAGER == null) return;
        ExtraScreenshotModule module = Anatorius.MODULE_MANAGER.getModule(ExtraScreenshotModule.class);
        if (module == null || !module.isToggled()) return;
        if (!(message.getContent() instanceof TranslatableTextContent content) || !content.getKey().equals("screenshot.success")) return;
        if (content.getArgs().length == 0 || !(content.getArgs()[0] instanceof Text name)) return;
        if (!(name.getStyle().getClickEvent() instanceof ClickEvent.OpenFile openFile)) return;

        File file = new File(openFile.path());
        CompletableFuture.runAsync(() -> {
            boolean copied = ClipboardImage.copy(file);
            lastCopied = copied;
            mc.execute(() -> {
                if (copied) Anatorius.CHAT_MANAGER.info("Screenshot copied to the clipboard.");
                else Anatorius.CHAT_MANAGER.error("Couldn't copy the screenshot to the clipboard.");
            });
        });
    }
}
