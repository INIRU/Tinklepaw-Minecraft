package dev.nyaru.hud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

public class NyaruHudClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(HudPayload.ID, HudPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(HudPayload.ID, (payload, context) ->
            HudState.update(new String(payload.data(), StandardCharsets.UTF_8))
        );
        HudElementRegistry.addLast(Identifier.of("nyaru-hud", "hud"), HudRenderer::render);
    }
}
