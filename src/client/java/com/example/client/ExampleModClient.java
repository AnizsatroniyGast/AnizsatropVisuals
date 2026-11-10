package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.MathHelper;
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
    static KeyBinding kMenu, kZoom, kHud;
    static boolean hud = true, prevFull = false, lastLmb = false;
    static int hits = 0;
    static LivingEntity target;
    static long targetTime = 0;
    static final ArrayDeque<Long> clicks = new ArrayDeque<>();
    static double lastX, lastZ, speed;
    static Integer savedFov = null;

    @Override
    public void onInitializeClient() {
        kMenu = key("key.visuals.menu", GLFW.GLFW_KEY_RIGHT_SHIFT);
        kZoom = key("key.visuals.zoom", GLFW.GLFW_KEY_Z);
        kHud = key("key.visuals.hud", GLFW.GLFW_KEY_H);

        ClientTickEvents.END_CLIENT_TICK.register(ExampleModClient::tick);
        HudRenderCallback.EVENT.register(ExampleModClient::render);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient && entity instanceof LivingEntity le) onHit(le);
            return ActionResult.PASS;
        });
    }

    static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(
            new KeyBinding(name, InputUtil.Type.KEYSYM, code, CAT));
    }

    static void onHit(LivingEntity e) {
        MinecraftClient mc = MinecraftClient.getInstance();
        hits++;
        target = e;
        targetTime = System.currentTimeMillis();
        if (HITSND.on) {
            mc.getSoundManager().play(PositionedSoundInstance.master(
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f));
        }
        if (HITPART.on && mc.world != null) {
            for (int i = 0; i < 8; i++) {
                mc.world.addParticle(ParticleTypes.CRIT,
                    e.getX() + (Math.random() - 0.5),
                    e.getBodyY(0.5) + (Math.random() - 0.5) * 0.5,
                    e.getZ() + (Math.random() - 0.5), 0, 0.1, 0);
            }
        }
    }

    static void tick(MinecraftClient mc) {
        if (mc.player == null) return;

        while (kHud.wasPressed()) hud = !hud;
        while (kMenu.wasPressed()) mc.setScreen(new MenuScreen());

        // Fullbright
        if (FULL.on) {
            if (!mc.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                mc.player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NIGHT_VISION, 100000, 0, false, false, false));
            }
        } else if (prevFull) {
            mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
        prevFull = FULL.on;

        // Toggle Sprint
        if (SPRINT.on && mc.currentScreen == null
            && mc.options.forwardKey.isPressed()
            && !mc.player.isSneaking()
            && !mc.player.horizontalCollision
            && mc.player.getHungerManager().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }

        // Zoom
        if (ZOOM.on && kZoom.isPressed() && mc.currentScreen == null) {
            if (savedFov == null) savedFov = mc.options.getFov().getValue();
            mc.options.getFov().setValue(30);
        } else if (savedFov != null) {
            mc.options.getFov().setValue(savedFov);
            savedFov = null;
        }

        // Auto Respawn
        if (RESPAWN.on && mc.player.isDead()) mc.player.requestRespawn();

        // Скорость
        double dx = mc.player.getX() - lastX;
        double dz = mc.player.getZ() - lastZ;
        speed = Math.sqrt(dx * dx + dz * dz) * 20;
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();

        // Trails
        if (TRAILS.on && mc.world != null && speed > 0.5) {
            mc.world.addParticle(ParticleTypes.END_ROD,
                mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ(), 0, 0, 0);
        }

        // CPS
        boolean lmb = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(),
            GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        long now = System.currentTimeMillis();
        if (lmb && !lastLmb && mc.currentScreen == null) clicks.add(now);
        lastLmb = lmb;
        while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000) clicks.pollFirst();
    }

    static int rainbow(float offset) {
        if (!RAINBOW.on) return 0xFF8F8FFF;
        float h = ((System.currentTimeMillis() % 4000L) / 4000f + offset) % 1f;
        return 0xFF000000 | MathHelper.hsvToRgb(h, 0.6f, 1f);
    }

    static int ping(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null || mc.player == null) return 0;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e == null ? 0 : e.getLatency();
    }

    static void render(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || !hud) return;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        long now = System.currentTimeMillis();
        int ly = 4; // следующая свободная строка слева

        // Info Panel
        if (PANEL.on) {
            List<String> lines = new ArrayList<>();
            lines.add("FPS: " + mc.getCurrentFps());
            lines.add("Ping: " + ping(mc) + " ms");
            lines.add("CPS: " + clicks.size());
            if (STREAMER.on) {
                lines.add("XYZ: hidden");
            } else {
                lines.add(String.format("XYZ: %.1f %.1f %.1f",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            }
            lines.add("Facing: " + mc.player.getHorizontalFacing().asString()
                + String.format(" (%.0f)", MathHelper.wrapDegrees(mc.player.getYaw())));
            lines.add(String.format("Speed: %.1f b/s", speed));
            if (HITCOUNT.on) lines.add("Hits: " + hits);
            lines.add(LocalTime.now().format(TIME));

            int w = 0;
            for (String s : lines) w = Math.max(w, tr.getWidth(s));
            int ph = lines.size() * 11;
            ctx.fill(2, 2, w + 12, ph + 8, 0x80000000);
            ctx.fill(2, 2, 4, ph + 8, rainbow(0f));
            int y = 5;
            for (String s : lines) {
                ctx.drawTextWithShadow(tr, s, 8, y, 0xFFFFFFFF);
                y += 11;
            }
            ly = ph + 12;
        }

        // Potion HUD
        if (POTIONS.on) {
            for (StatusEffectInstance inst : mc.player.getStatusEffects()) {
                String name = inst.getEffectType().value().getName().getString()
                    + " " + (inst.getAmplifier() + 1);
                int secs = inst.getDuration() / 20;
                String t = inst.isInfinite() ? "inf" : String.format("%d:%02d", secs / 60, secs % 60);
                ctx.drawTextWithShadow(tr, name + "  " + t, 6, ly, 0xFFAAAAFF);
                ly += 11;
            }
        }

        // TNT Timer
        if (TNT.on && mc.world != null) {
            int shown = 0;
            for (Entity e : mc.world.getEntities()) {
                if (shown >= 5) break;
                if (e instanceof TntEntity t && t.distanceTo(mc.player) < 64) {
                    String s = String.format("TNT %.1fs  %.0fm", t.getFuse() / 20f, t.distanceTo(mc.player));
                    ctx.drawTextWithShadow(tr, s, 6, ly, 0xFFFF5555);
                    ly += 11;
                    shown++;
                }
            }
        }

        // Armor HUD (справа сверху)
        if (ARMOR.on) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
            int iy = 4;
            for (EquipmentSlot slot : slots) {
                ItemStack st = mc.player.getEquippedStack(slot);
                if (st.isEmpty()) continue;
                ctx.drawItem(st, sw - 22, iy);
                ctx.drawStackOverlay(tr, st, sw - 22, iy);
                iy += 18;
            }
        }

        // Module List
        if (MODLIST.on) {
            int my = 6;
            int i = 0;
            for (Mod m : MODS) {
                if (!m.on || m == MODLIST || m == PANEL) continue;
                int mw = tr.getWidth(m.name);
                ctx.drawTextWithShadow(tr, m.name, sw - 28 - mw, my, rainbow(i * 0.1f));
                my += 11;
                i++;
            }
        }

        // Keystrokes
        if (KEYS.on) {
            int x0 = sw - 70;
            int y0 = sh - 72;
            drawKey(ctx, tr, "W", x0 + 22, y0, 20, 20, mc.options.forwardKey.isPressed());
            drawKey(ctx, tr, "A", x0, y0 + 22, 20, 20, mc.options.leftKey.isPressed());
            drawKey(ctx, tr, "S", x0 + 22, y0 + 22, 20, 20, mc.options.backKey.isPressed());
            drawKey(ctx, tr, "D", x0 + 44, y0 + 22, 20, 20, mc.options.rightKey.isPressed());
            drawKey(ctx, tr, "LMB", x0, y0 + 44, 31, 18, mc.options.attackKey.isPressed());
            drawKey(ctx, tr, "RMB", x0 + 33, y0 + 44, 31, 18, mc.options.useKey.isPressed());
        }

        // Totem Bar
        if (TOTEMS.on) {
            int n = 0;
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack st = inv.getStack(i);
                if (st.isOf(Items.TOTEM_OF_UNDYING)) n += st.getCount();
            }
            int tx = sw / 2 - 18;
            int ty = sh - 76;
            ctx.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), tx, ty);
            ctx.drawTextWithShadow(tr, "x" + n, tx + 18, ty + 5, n > 0 ? 0xFFFFFFFF : 0xFFFF5555);
        }

        // Target HUD
        if (TARGET.on && target != null && target.isAlive() && now - targetTime < 4000) {
            int bx = sw / 2 + 20;
            int by = sh / 2 + 20;
            ctx.fill(bx, by, bx + 110, by + 34, 0x90000000);
            ctx.drawTextWithShadow(tr, target.getName().getString(), bx + 4, by + 3, 0xFFFFFFFF);
            float frac = MathHelper.clamp(target.getHealth() / target.getMaxHealth(), 0f, 1f);
            int col = frac > 0.5f ? 0xFF55FF55 : (frac > 0.25f ? 0xFFFFAA00 : 0xFFFF5555);
            ctx.fill(bx + 4, by + 14, bx + 106, by + 20, 0xFF333333);
            ctx.fill(bx + 4, by + 14, bx + 4 + (int) (102 * frac), by + 20, col);
            ctx.drawTextWithShadow(tr, String.format("%.1f HP  %.1fm",
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
            ctx.drawCenteredTextWithShadow(tr, "LOW HP", sw / 2, 30, 0xFFFF5555);
        }
    }

    static void drawKey(DrawContext ctx, TextRenderer tr, String label,
                        int x, int y, int w, int h, boolean pressed) {
        ctx.fill(x, y, x + w, y + h, pressed ? 0xB0FFFFFF : 0x80000000);
        int tw = tr.getWidth(label);
        ctx.drawText(tr, label, x + (w - tw) / 2, y + (h - 8) / 2,
            pressed ? 0xFF000000 : 0xFFFFFFFF, false);
    }

    // ===== Меню =====
    static class MenuScreen extends Screen {
        int tab = 0;

        MenuScreen() {
            super(Text.literal("Anizsatrop Visuals"));
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        public void renderBackground(DrawContext c, int mx, int my, float d) {
            // свой фон рисуем в render()
        }

        int px() { return width / 2 - 150; }
        int py() { return height / 2 - 105; }

        @Override
        public void render(DrawContext c, int mx, int my, float d) {
            c.fill(0, 0, width, height, 0x90000000);
            int x = px(), y = py();
            c.fill(x, y, x + 300, y + 210, 0xE0101018);
            c.fill(x, y, x + 300, y + 2, rainbow(0f));
            c.drawTextWithShadow(textRenderer, "Anizsatrop Visuals", x + 8, y + 8, 0xFFFFFFFF);

            for (int i = 0; i < CATS.length; i++) {
                int tx = x + 8 + i * 73;
                boolean sel = i == tab;
                c.fill(tx, y + 22, tx + 70, y + 40, sel ? 0xFF3A3A5A : 0xFF22222E);
                c.drawCenteredTextWithShadow(textRenderer, CATS[i], tx + 35, y + 27, 0xFFFFFFFF);
            }

            int row = 0;
            for (Mod m : MODS) {
                if (!m.cat.equals(CATS[tab])) continue;
                int ry = y + 48 + row * 24;
                boolean hover = mx >= x + 8 && mx <= x + 292 && my >= ry && my < ry + 22;
                c.fill(x + 8, ry, x + 292, ry + 22, hover ? 0xFF2E2E40 : 0xFF1E1E2A);
                c.drawTextWithShadow(textRenderer, m.name, x + 14, ry + 3, 0xFFFFFFFF);
                c.drawTextWithShadow(textRenderer, m.desc, x + 14, ry + 12, 0xFF888899);
                c.fill(x + 262, ry + 5, x + 286, ry + 17, m.on ? 0xFF3CB371 : 0xFF55555F);
                int kx = m.on ? x + 275 : x + 264;
                c.fill(kx, ry + 7, kx + 9, ry + 15, 0xFFFFFFFF);
                row++;
            }
            c.drawTextWithShadow(textRenderer, "Right Shift - close", x + 8, y + 196, 0xFF777788);
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
                close();
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
    }
                }
