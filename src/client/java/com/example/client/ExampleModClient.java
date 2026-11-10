package com.example.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class ExampleModClient implements ClientModInitializer {
    static final String CAT = "Anizsatrop Visuals";
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ===== Модули =====
    static class Mod {
        final String name, cat, desc;
        boolean on;
        Mod(String n, String c, String d, boolean o) { name = n; cat = c; desc = d; on = o; }
    }

    static final List<Mod> MODS = new ArrayList<>();
    static final String[] CATS = {"HUD", "Visual", "Combat", "Utility"};

    static Mod add(String n, String c, String d, boolean o) {
        Mod m = new Mod(n, c, d, o);
        MODS.add(m);
        return m;
    }

    // HUD
    static final Mod PANEL = add("Info Panel", "HUD", "FPS, ping, CPS, XYZ, speed", true);
    static final Mod KEYS = add("Keystrokes", "HUD", "WASD and mouse buttons", true);
    static final Mod ARMOR = add("Armor HUD", "HUD", "Armor and held item", true);
    static final Mod POTIONS = add("Potion HUD", "HUD", "Active effects list", true);
    static final Mod MODLIST = add("Module List", "HUD", "Enabled modules on screen", true);
    static final Mod STREAMER = add("Streamer Mode", "HUD", "Hides your coordinates", false);
    // Visual
    static final Mod FULL = add("Fullbright", "Visual", "Night vision effect", false);
    static final Mod ZOOM = add("Zoom", "Visual", "Hold Z to zoom", true);
    static final Mod TRAILS = add("Trails", "Visual", "Particles behind you", false);
    static final Mod HITPART = add("Hit Particles", "Visual", "Particles on hit", true);
    static final Mod RAINBOW = add("Rainbow", "Visual", "Rainbow HUD colors", true);
    // Combat
    static final Mod HITCOUNT = add("Hit Counter", "Combat", "Counts your hits", true);
    static final Mod TARGET = add("Target HUD", "Combat", "Target health and distance", true);
    static final Mod HITSND = add("Hit Sounds", "Combat", "Sound when you hit", false);
    static final Mod LOWHP = add("Low HP", "Combat", "Warning at low health", true);
    static final Mod TOTEMS = add("Totem Bar", "Combat", "Totems in inventory", true);
    static final Mod TNT = add("TNT Timer", "Combat", "Fuse time of nearby TNT", true);
    // Utility
    static final Mod SPRINT = add("Toggle Sprint", "Utility", "Always sprint forward", false);
    static final Mod RESPAWN = add("Auto Respawn", "Utility", "Respawn automatically", false);

    // ===== Состояние =====
    static KeyMapping kMenu, kZoom, kHud;
    static boolean hud = true, prevFull = false, lastLmb = false;
    static int hits = 0;
    static LivingEntity target;
    static long targetTime = 0;
    static final ArrayDeque<Long> clicks = new ArrayDeque<>();
    static double lastX, lastZ, speed;
    static Integer savedFov = null;
    static int fpsFrames = 0, fpsValue = 0;
    static long fpsLast = 0;

    @Override
    public void onInitializeClient() {
        kMenu = key("key.visuals.menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        kZoom = key("key.visuals.zoom", GLFW.GLFW_KEY_Z);
        kHud = key("key.visuals.hud", GLFW.GLFW_KEY_H);

        ClientTickEvents.END_CLIENT_TICK.register(ExampleModClient::tick);
        HudRenderCallback.EVENT.register(ExampleModClient::render);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide && entity instanceof LivingEntity le) onHit(le);
            return InteractionResult.PASS;
        });
    }

    static KeyMapping key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(
            new KeyMapping(name, InputConstants.Type.KEYSYM, code, CAT));
    }

    static void onHit(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        hits++;
        target = e;
        targetTime = System.currentTimeMillis();
        if (HITSND.on) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5f));
        }
        if (HITPART.on && mc.level != null) {
            for (int i = 0; i < 8; i++) {
                mc.level.addParticle(ParticleTypes.CRIT,
                    e.getX() + (Math.random() - 0.5),
                    e.getY() + e.getBbHeight() / 2 + (Math.random() - 0.5) * 0.5,
                    e.getZ() + (Math.random() - 0.5), 0, 0.1, 0);
            }
        }
    }

    static void tick(Minecraft mc) {
        if (mc.player == null) return;

        while (kHud.consumeClick()) hud = !hud;
        while (kMenu.consumeClick()) mc.setScreen(new MenuScreen());

        // Fullbright
        if (FULL.on) {
            if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
                mc.player.addEffect(new MobEffectInstance(
                    MobEffects.NIGHT_VISION, 100000, 0, false, false, false));
            }
        } else if (prevFull) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
        prevFull = FULL.on;

        // Toggle Sprint
        if (SPRINT.on && mc.screen == null
            && mc.options.keyUp.isDown()
            && !mc.player.isShiftKeyDown()
            && !mc.player.horizontalCollision
            && mc.player.getFoodData().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }

        // Zoom
        if (ZOOM.on && kZoom.isDown() && mc.screen == null) {
            if (savedFov == null) savedFov = mc.options.fov().get();
            mc.options.fov().set(30);
        } else if (savedFov != null) {
            mc.options.fov().set(savedFov);
            savedFov = null;
        }

        // Auto Respawn
        if (RESPAWN.on && mc.player.isDeadOrDying()) mc.player.respawn();

        // Скорость
        double dx = mc.player.getX() - lastX;
        double dz = mc.player.getZ() - lastZ;
        speed = Math.sqrt(dx * dx + dz * dz) * 20;
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();

        // Trails
        if (TRAILS.on && mc.level != null && speed > 0.5) {
            mc.level.addParticle(ParticleTypes.END_ROD,
                mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ(), 0, 0, 0);
        }

        // CPS
        boolean lmb = mc.options.keyAttack.isDown();
        long now = System.currentTimeMillis();
        if (lmb && !lastLmb && mc.screen == null) clicks.add(now);
        lastLmb = lmb;
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();
    }

    static int rainbow(float offset) {
        if (!RAINBOW.on) return 0xFF8F8FFF;
        float h = ((System.currentTimeMillis() % 4000L) / 4000f + offset) % 1f;
        return 0xFF000000 | Mth.hsvToRgb(h, 0.6f, 1f);
    }

    static int ping(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) return 0;
        PlayerInfo e = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return e == null ? 0 : e.getLatency();
    }

    static void render(GuiGraphics ctx, DeltaTracker tc) {
        // Счётчик FPS (свой, чтобы не зависеть от названий методов)
        long now = System.currentTimeMillis();
        fpsFrames++;
        if (now - fpsLast >= 1000) {
            fpsValue = fpsFrames;
            fpsFrames = 0;
            fpsLast = now;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !hud) return;
        Font tr = mc.font;
        int sw = ctx.guiWidth();
        int sh = ctx.guiHeight();
        int ly = 4;

        // Info Panel
        if (PANEL.on) {
            List<String> lines = new ArrayList<>();
            lines.add("FPS: " + fpsValue);
            lines.add("Ping: " + ping(mc) + " ms");
            lines.add("CPS: " + clicks.size());
            if (STREAMER.on) {
                lines.add("XYZ: hidden");
            } else {
                lines.add(String.format("XYZ: %.1f %.1f %.1f",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            }
            lines.add("Facing: " + mc.player.getDirection().getName()
                + String.format(" (%.0f)", Mth.wrapDegrees(mc.player.getYRot())));
            lines.add(String.format("Speed: %.1f b/s", speed));
            if (HITCOUNT.on) lines.add("Hits: " + hits);
            lines.add(LocalTime.now().format(TIME));

            int w = 0;
            for (String s : lines) w = Math.max(w, tr.width(s));
            int ph = lines.size() * 11;
            ctx.fill(2, 2, w + 12, ph + 8, 0x80000000);
            ctx.fill(2, 2, 4, ph + 8, rainbow(0f));
            int y = 5;
            for (String s : lines) {
                ctx.drawString(tr, s, 8, y, 0xFFFFFFFF);
                y += 11;
            }
            ly = ph + 12;
        }

        // Potion HUD
        if (POTIONS.on) {
            for (MobEffectInstance inst : mc.player.getActiveEffects()) {
                String name = inst.getEffect().value().getDisplayName().getString()
                    + " " + (inst.getAmplifier() + 1);
                int secs = inst.getDuration() / 20;
                String t = inst.isInfiniteDuration() ? "inf" : String.format("%d:%02d", secs / 60, secs % 60);
                ctx.drawString(tr, name + "  " + t, 6, ly, 0xFFAAAAFF);
                ly += 11;
            }
        }

        // TNT Timer
        if (TNT.on && mc.level != null) {
            int shown = 0;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (shown >= 5) break;
                if (e instanceof PrimedTnt t && t.distanceTo(mc.player) < 64) {
                    String s = String.format("TNT %.1fs  %.0fm", t.getFuse() / 20f, t.distanceTo(mc.player));
                    ctx.drawString(tr, s, 6, ly, 0xFFFF5555);
                    ly += 11;
                    shown++;
                }
            }
        }

        // Armor HUD
        if (ARMOR.on) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
            int iy = 4;
            for (EquipmentSlot slot : slots) {
                ItemStack st = mc.player.getItemBySlot(slot);
                if (st.isEmpty()) continue;
                ctx.renderItem(st, sw - 22, iy);
                ctx.renderItemDecorations(tr, st, sw - 22, iy);
                iy += 18;
            }
        }

        // Module List
        if (MODLIST.on) {
            int my = 6;
            int i = 0;
            for (Mod m : MODS) {
                if (!m.on || m == MODLIST || m == PANEL) continue;
                int mw = tr.width(m.name);
                ctx.drawString(tr, m.name, sw - 28 - mw, my, rainbow(i * 0.1f));
                my += 11;
                i++;
            }
        }

        // Keystrokes
        if (KEYS.on) {
            int x0 = sw - 70;
            int y0 = sh - 72;
            drawKey(ctx, tr, "W", x0 + 22, y0, 20, 20, mc.options.keyUp.isDown());
            drawKey(ctx, tr, "A", x0, y0 + 22, 20, 20, mc.options.keyLeft.isDown());
            drawKey(ctx, tr, "S", x0 + 22, y0 + 22, 20, 20, mc.options.keyDown.isDown());
            drawKey(ctx, tr, "D", x0 + 44, y0 + 22, 20, 20, mc.options.keyRight.isDown());
            drawKey(ctx, tr, "LMB", x0, y0 + 44, 31, 18, mc.options.keyAttack.isDown());
            drawKey(ctx, tr, "RMB", x0 + 33, y0 + 44, 31, 18, mc.options.keyUse.isDown());
        }

        // Totem Bar
        if (TOTEMS.on) {
            int n = 0;
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (st.is(Items.TOTEM_OF_UNDYING)) n += st.getCount();
            }
            int tx = sw / 2 - 18;
            int ty = sh - 76;
            ctx.renderItem(new ItemStack(Items.TOTEM_OF_UNDYING), tx, ty);
            ctx.drawString(tr, "x" + n, tx + 18, ty + 5, n > 0 ? 0xFFFFFFFF : 0xFFFF5555);
        }

        // Target HUD
        if (TARGET.on && target != null && target.isAlive() && now - targetTime < 4000) {
            int bx = sw / 2 + 20;
            int by = sh / 2 + 20;
            ctx.fill(bx, by, bx + 110, by + 34, 0x90000000);
            ctx.drawString(tr, target.getName().getString(), bx + 4, by + 3, 0xFFFFFFFF);
            float frac = Mth.clamp(target.getHealth() / target.getMaxHealth(), 0f, 1f);
            int col = frac > 0.5f ? 0xFF55FF55 : (frac > 0.25f ? 0xFFFFAA00 : 0xFFFF5555);
            ctx.fill(bx + 4, by + 14, bx + 106, by + 20, 0xFF333333);
            ctx.fill(bx + 4, by + 14, bx + 4 + (int) (102 * frac), by + 20, col);
            ctx.drawString(tr, String.format("%.1f HP  %.1fm",
                target.getHealth(), target.distanceTo(mc.player)), bx + 4, by + 23, 0xFFCCCCCC);
        }

        // Low HP
        float hp = mc.player.getHealth();
        if (LOWHP.on && hp > 0 && hp <= 6f) {
            int c = 0x66FF0000;
            ctx.fill(0, 0, sw, 6, c);
            ctx.fill(0, sh - 6, sw, sh, c);
            ctx.fill(0, 0, 6, sh, c);
            ctx.fill(sw - 6, 0, sw, sh, c);
            ctx.drawCenteredString(tr, "LOW HP", sw / 2, 30, 0xFFFF5555);
        }
    }

    static void drawKey(GuiGraphics ctx, Font tr, String label,
                        int x, int y, int w, int h, boolean pressed) {
        ctx.fill(x, y, x + w, y + h, pressed ? 0xB0FFFFFF : 0x80000000);
        int tw = tr.width(label);
        ctx.drawString(tr, label, x + (w - tw) / 2, y + (h - 8) / 2,
            pressed ? 0xFF000000 : 0xFFFFFFFF, false);
    }

    // ===== Меню =====
    static class MenuScreen extends Screen {
        int tab = 0;

        MenuScreen() {
            super(Component.literal("Anizsatrop Visuals"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void renderBackground(GuiGraphics c, int mx, int my, float d) {
            // фон рисуем в render()
        }

        int px() { return width / 2 - 150; }
        int py() { return height / 2 - 105; }

        @Override
        public void render(GuiGraphics c, int mx, int my, float d) {
            c.fill(0, 0, width, height, 0x90000000);
            int x = px(), y = py();
            c.fill(x, y, x + 300, y + 210, 0xE0101018);
            c.fill(x, y, x + 300, y + 2, rainbow(0f));
            c.drawString(font, "Anizsatrop Visuals", x + 8, y + 8, 0xFFFFFFFF);

            for (int i = 0; i < CATS.length; i++) {
                int tx = x + 8 + i * 73;
                boolean sel = i == tab;
                c.fill(tx, y + 22, tx + 70, y + 40, sel ? 0xFF3A3A5A : 0xFF22222E);
                c.drawCenteredString(font, CATS[i], tx + 35, y + 27, 0xFFFFFFFF);
            }

            int row = 0;
            for (Mod m : MODS) {
                if (!m.cat.equals(CATS[tab])) continue;
                int ry = y + 48 + row * 24;
                boolean hover = mx >= x + 8 && mx <= x + 292 && my >= ry && my < ry + 22;
                c.fill(x + 8, ry, x + 292, ry + 22, hover ? 0xFF2E2E40 : 0xFF1E1E2A);
                c.drawString(font, m.name, x + 14, ry + 3, 0xFFFFFFFF);
                c.drawString(font, m.desc, x + 14, ry + 12, 0xFF888899);
                c.fill(x + 262, ry + 5, x + 286, ry + 17, m.on ? 0xFF3CB371 : 0xFF55555F);
                int kx = m.on ? x + 275 : x + 264;
                c.fill(kx, ry + 7, kx + 9, ry + 15, 0xFFFFFFFF);
                row++;
            }
            c.drawString(font, "Right Shift - close", x + 8, y + 196, 0xFF777788);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int x = px(), y = py();
            for (int i = 0; i < CATS.length; i++) {
                int tx = x + 8 + i * 73;
                if (mx >= tx && mx < tx + 70 && my >= y + 22 && my < y + 40) {
                    tab = i;
                    return true;
                }
            }
            int row = 0;
            for (Mod m : MODS) {
                if (!m.cat.equals(CATS[tab])) continue;
                int ry = y + 48 + row * 24;
                if (mx >= x + 8 && mx <= x + 292 && my >= ry && my < ry + 22) {
                    m.on = !m.on;
                    return true;
                }
                row++;
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public boolean keyPressed(int key, int scan, int modifiers) {
            if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                onClose();
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
    }
}
