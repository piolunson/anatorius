package dev.project.anatorius.mixins;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.project.anatorius.Anatorius;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.command.CommandSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.concurrent.CompletableFuture;

@Mixin(ChatInputSuggestor.class)
public abstract class ChatInputSuggestorMixin {
    @Final
    @Shadow
    private TextFieldWidget textField;

    @Shadow
    private boolean completingSuggestions;

    @Shadow
    private ParseResults<CommandSource> parse;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    private ChatInputSuggestor.SuggestionWindow window;

    @Shadow
    protected abstract void showCommandSuggestions();

    @Unique
    private boolean anatorius$usingClientCommands = false;

    @Inject(
        method = "refresh",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/brigadier/StringReader;canRead()Z",
            remap = false
        ),
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void refreshHook(CallbackInfo ci, String string, StringReader reader) {
        String prefix = Anatorius.COMMAND_MANAGER.getPrefix();
        int prefixLength = prefix.length();

        if (reader.canRead(prefixLength) && reader.getString().startsWith(prefix, reader.getCursor())) {
            anatorius$usingClientCommands = true;

            reader.setCursor(reader.getCursor() + prefixLength);

            if (parse == null) {
                parse = Anatorius.COMMAND_MANAGER.getDispatcher().parse(reader, Anatorius.COMMAND_MANAGER.getSource());
            }

            final int cursor = textField.getCursor();

            if (cursor >= prefixLength && (window == null || !completingSuggestions)) {
                pendingSuggestions = Anatorius.COMMAND_MANAGER.getDispatcher().getCompletionSuggestions(parse, cursor);
                pendingSuggestions.thenRun(() -> {
                    if (pendingSuggestions.isDone()) {
                        showCommandSuggestions();
                    }
                });
            }

            ci.cancel();
        } else {
            anatorius$usingClientCommands = false;
        }
    }
}
