package dev.project.anatorius.commands.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.commands.Command;
import dev.project.anatorius.commands.RegisterCommand;
import dev.project.anatorius.modules.impl.core.NotificationsModule;
import dev.project.anatorius.utils.chat.ChatUtils;

import java.util.ArrayList;
import java.util.List;

@RegisterCommand(name = "chatnotify", tag = "ChatNotify", description = "Turns module toggle notifications on or off, or picks where they show up.", syntax = "<true|false|reset> | <theme> <Classic|Overlay|Both>")
public class ChatNotifyCommand extends Command {
    @Override
    public void execute(String[] args) {
        NotificationsModule notifications = Anatorius.MODULE_MANAGER.getModule(NotificationsModule.class);
        if (notifications == null) return;

        if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "true" -> notifications.chatNotify.setValue(true);
                case "false" -> notifications.chatNotify.setValue(false);
                case "reset" -> notifications.chatNotify.resetValue();
                default -> {
                    messageSyntax();
                    return;
                }
            }
            Anatorius.CHAT_MANAGER.tagged("Toggle notifications are now " + ChatUtils.getPrimary() + (notifications.chatNotify.getValue() ? "on" : "off") + ChatUtils.getSecondary() + ".", getTag(), getName());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("theme")) {
            String mode = notifications.theme.getModes().stream().filter(args[1]::equalsIgnoreCase).findFirst().orElse(null);
            if (mode == null) {
                Anatorius.CHAT_MANAGER.tagged("Please input a valid theme: " + ChatUtils.getPrimary() + String.join(ChatUtils.getSecondary() + ", " + ChatUtils.getPrimary(), notifications.theme.getModes()) + ChatUtils.getSecondary() + ".", getTag(), getName());
                return;
            }
            notifications.theme.setValue(mode);
            Anatorius.CHAT_MANAGER.tagged("Successfully set the notification theme to " + ChatUtils.getPrimary() + mode + ChatUtils.getSecondary() + ".", getTag(), getName());
        } else {
            messageSyntax();
        }
    }

    @Override
    public List<String> suggest(String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 0) {
            out.add("true"); out.add("false"); out.add("reset"); out.add("theme");
        } else if (args.length == 1 && args[0].equalsIgnoreCase("theme")) {
            NotificationsModule notifications = Anatorius.MODULE_MANAGER.getModule(NotificationsModule.class);
            if (notifications != null) out.addAll(notifications.theme.getModes());
        }
        return out;
    }
}
