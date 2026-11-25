package dev.project.anatorius.utils.mixins;

public interface IChatHudLineVisible {
    boolean anatorius$isClientMessage();

    void anatorius$setClientMessage(boolean clientMessage);

    String anatorius$getClientIdentifier();

    void anatorius$setClientIdentifier(String clientIdentifier);
}
