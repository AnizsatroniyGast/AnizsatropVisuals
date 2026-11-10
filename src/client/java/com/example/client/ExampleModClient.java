package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

public class ExampleModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;

            String fps = "FPS: " + mc.getCurrentFps();
            String xyz = String.format("XYZ: %.1f %.1f %.1f",
                mc.player.getX(), mc.player.getY(), mc.player.getZ());

            ctx.drawTextWithShadow(mc.textRenderer, fps, 5, 5, 0xFFFFFFFF);
            ctx.drawTextWithShadow(mc.textRenderer, xyz, 5, 17, 0xFFFFFFFF);
        });
    }
}
