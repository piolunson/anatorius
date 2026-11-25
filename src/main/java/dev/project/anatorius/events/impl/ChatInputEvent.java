package dev.project.anatorius.events.impl;

import lombok.*;
import dev.project.anatorius.events.Event;

@Getter @Setter @AllArgsConstructor
public class ChatInputEvent extends Event {
    private String message;
}
