package com.github.lutzluca.btrbz.utils;

import io.vavr.control.Try;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

@Slf4j
public final class ClientTickDispatcher {

    private static final List<ClientTickEvents.EndTick> LISTENERS = new CopyOnWriteArrayList<>();

    static {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickDispatcher::onEndTick);
    }

    private static void onEndTick(MinecraftClient client) {
        LISTENERS.forEach(listener -> Try
            .run(() -> listener.onEndTick(client))
            .onFailure(err -> log.warn("Exception in client end tick listener", err)));
    }

    public static void register(ClientTickEvents.EndTick listener) {
        LISTENERS.add(listener);
    }

    public static void unregister(ClientTickEvents.EndTick listener) {
        LISTENERS.remove(listener);
    }
}
