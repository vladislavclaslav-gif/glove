package com.jackpotind.client;

import com.jackpotind.JackpotIndustries;
import com.jackpotind.upgrader.ItemValues;
import com.jackpotind.upgrader.UpgraderMenu;
import com.jackpotind.upgrader.UpgraderNetwork;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Окно Апгрейд-автомата «Джекпот»: неоновая панель с мигающими лампочками,
 * колесо удачи (зелёный сектор — шанс выигрыша), каталог целей с поиском.
 */
public class JackpotScreen extends AbstractContainerScreen<UpgraderMenu> {
    private static final ResourceLocation BG = JackpotIndustries.id("textures/gui/jackpot.png");
    private static final ResourceLocation WHEEL_ID = JackpotIndustries.id("dynamic/wheel");
    private static final int WHEEL = 64, CX = 88, CY = 50;          // колесо: размер и центр
    private static final int TX = 138, TY = 30;                     // окошко цели
    private static final int COLS = 9, ROWS = 9, CELL = 18;          // каталог

    private Button spinButton;
    private Button modeButton;
    private int shownMode = -1;
    private EditBox search;
    private boolean catalogOpen;
    private List<Item> allItems = List.of();
    private final List<Item> filtered = new ArrayList<>();
    private int scrollRow;

    private DynamicTexture wheelTexture;
    private int wheelChance = -1;

    private int ticks;
    private int lastSpinId;
    private int animStart = -10_000;
    private float restAngle;        // где колесо остановилось в прошлый раз
    private float shownAngle;
    private int lastTickSector;
    private boolean resultSoundPlayed = true;

    public JackpotScreen(UpgraderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 192;
        inventoryLabelY = 98;
    }

    @Override
    protected void init() {
        super.init();
        spinButton = addRenderableWidget(Button.builder(Component.translatable("gui.jackpotind.spin"),
                b -> press(UpgraderMenu.BTN_SPIN)).bounds(leftPos + 124, topPos + 64, 48, 15).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> press(percentMode()
                        ? (hasShiftDown() ? UpgraderMenu.BTN_PCT_MINUS1 : UpgraderMenu.BTN_PCT_MINUS5)
                        : (hasShiftDown() ? UpgraderMenu.BTN_MINUS10 : UpgraderMenu.BTN_MINUS)))
                .bounds(leftPos + 124, topPos + 50, 12, 12).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> press(percentMode()
                        ? (hasShiftDown() ? UpgraderMenu.BTN_PCT_PLUS1 : UpgraderMenu.BTN_PCT_PLUS5)
                        : (hasShiftDown() ? UpgraderMenu.BTN_PLUS10 : UpgraderMenu.BTN_PLUS)))
                .bounds(leftPos + 160, topPos + 50, 12, 12).build());
        modeButton = addRenderableWidget(Button.builder(modeText(), b -> press(UpgraderMenu.BTN_MODE))
                .bounds(leftPos + 124, topPos + 81, 48, 12).build());

        search = new EditBox(font, leftPos + 8, topPos + 6, 140, 12, Component.empty());
        search.setMaxLength(50);
        search.setHint(Component.translatable("gui.jackpotind.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(s -> refilter());
        allItems = BuiltInRegistries.ITEM.stream()
                .filter(i -> i != Items.AIR && !new ItemStack(i).is(ItemValues.BLACKLIST))
                .toList();
        refilter();
        lastSpinId = menu.spinId();

        if (wheelTexture == null) {
            wheelTexture = new DynamicTexture(WHEEL, WHEEL, true);
            minecraft.getTextureManager().register(WHEEL_ID, wheelTexture);
        }
        wheelChance = -1;
    }

    @Override
    public void removed() {
        super.removed();
        if (wheelTexture != null) {
            minecraft.getTextureManager().release(WHEEL_ID);
            wheelTexture = null;
        }
    }

    private boolean percentMode() { return menu.mode() == UpgraderMenu.MODE_PERCENT; }

    private Component modeText() {
        return Component.translatable(percentMode() ? "gui.jackpotind.mode_percent" : "gui.jackpotind.mode_target");
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void refilter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Item item : allItems) {
            if (q.isEmpty() || item.getDescription().getString().toLowerCase(Locale.ROOT).contains(q)
                    || BuiltInRegistries.ITEM.getKey(item).toString().contains(q)) filtered.add(item);
        }
        scrollRow = 0;
    }

    private int maxScroll() { return Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS); }

    private boolean animating() { return ticks - animStart < UpgraderMenu.SPIN_TICKS; }

    private boolean showingResult() {
        return menu.result() != 0 && !animating() && ticks - animStart < UpgraderMenu.SPIN_TICKS + 60;
    }

    // ---------------- колесо ----------------
    private void redrawWheel() {
        int chance = (int) Math.round(menu.chance() * 10000);
        if (chance == wheelChance || wheelTexture == null || wheelTexture.getPixels() == null) return;
        wheelChance = chance;
        NativeImage img = wheelTexture.getPixels();
        double r = WHEEL / 2.0;
        double greenDeg = menu.chance() * 360.0;
        for (int y = 0; y < WHEEL; y++) {
            for (int x = 0; x < WHEEL; x++) {
                double dx = x + 0.5 - r, dy = y + 0.5 - r;
                double dist = Math.sqrt(dx * dx + dy * dy);
                int argb;
                if (dist > r) {
                    argb = 0;
                } else if (dist > r - 3) {
                    argb = 0xFFFFC828;                                   // золотой обод
                } else if (dist < 9) {
                    argb = dist > 7.5 ? 0xFFFFC828 : 0xFF1A0E28;         // ступица
                } else {
                    double deg = Math.toDegrees(Math.atan2(dx, -dy));
                    if (deg < 0) deg += 360;
                    boolean win = deg < greenDeg;
                    boolean stripe = ((int) (deg / 15)) % 2 == 0;
                    argb = win ? (stripe ? 0xFF2EE66B : 0xFF1FB453) : (stripe ? 0xFFB3243A : 0xFF8A1A2E);
                    if (Math.abs(deg % 30) < 1.2 && dist > r - 8) argb = 0xFFFFE9A0; // насечки
                }
                img.setPixelRGBA(x, y, argbToAbgr(argb));
            }
        }
        wheelTexture.upload();
    }

    private static int argbToAbgr(int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    /** Угол поворота колеса: 5 оборотов с замедлением, остановка точно на выпавшем значении. */
    private float wheelAngle(float partial) {
        if (menu.result() == 0) return 0;
        float target = 360f * 5 - (float) (menu.roll() * 360.0);
        if (!animating()) return target;
        float t = Math.min(1f, (ticks - animStart + partial) / UpgraderMenu.SPIN_TICKS);
        float eased = (float) (1 - Math.pow(1 - t, 4));
        return restAngle + (target - restAngle) * eased;
    }

    private void play(SoundEvent sound, float pitch, float volume) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        search.tick();
        if (menu.spinId() != lastSpinId) {
            lastSpinId = menu.spinId();
            restAngle = shownAngle % 360f;
            animStart = ticks;
            lastTickSector = 0;
            resultSoundPlayed = false;
            play(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.6f);
        }
        if (animating()) {
            int sector = (int) (wheelAngle(0) / 30f);
            if (sector != lastTickSector) {
                lastTickSector = sector;
                play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.2f + (ticks - animStart) / (float) UpgraderMenu.SPIN_TICKS * 0.6f, 0.5f);
            }
        } else if (!resultSoundPlayed && menu.result() != 0) {
            resultSoundPlayed = true;
            if (menu.result() == 1 && menu.secret() > 0) play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            else if (menu.result() == 1) play(SoundEvents.PLAYER_LEVELUP, 1.0f, 0.8f);
            else play(SoundEvents.VILLAGER_NO, 0.8f, 0.8f);
        }
        spinButton.active = !menu.isSpinning() && !animating() && menu.chance() > 0;
        if (shownMode != menu.mode()) {
            shownMode = menu.mode();
            modeButton.setMessage(modeText());
        }
    }

    private static boolean isOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------- отрисовка ----------------
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.blit(BG, x, y, 0, 0, imageWidth, imageHeight, 256, 256);

        // мигающие лампочки по краю: быстрее во время прокрутки, зелёные/красные после результата
        int speed = animating() ? 2 : 8;
        int phase = ticks / speed;
        for (int i = 0, lx = 6; lx < imageWidth - 6; lx += 10, i++) {
            g.fill(x + lx, y + 1, x + lx + 2, y + 3, bulb(i + phase));
            g.fill(x + lx, y + imageHeight - 3, x + lx + 2, y + imageHeight - 1, bulb(i + phase + 1));
        }
        for (int i = 0, ly = 8; ly < imageHeight - 8; ly += 10, i++) {
            g.fill(x + 1, y + ly, x + 3, y + ly + 2, bulb(i + phase + 1));
            g.fill(x + imageWidth - 3, y + ly, x + imageWidth - 1, y + ly + 2, bulb(i + phase));
        }

        // колесо
        redrawWheel();
        g.pose().pushPose();
        g.pose().translate(x + CX, y + CY, 0);
        shownAngle = wheelAngle(partialTick);
        g.pose().mulPose(Axis.ZP.rotationDegrees(shownAngle));
        g.blit(WHEEL_ID, -WHEEL / 2, -WHEEL / 2, 0, 0, WHEEL, WHEEL, WHEEL, WHEEL);
        g.pose().popPose();
        // стрелка сверху
        for (int i = 0; i < 5; i++) g.fill(x + CX - 4 + i, y + CY - 35 + i, x + CX + 5 - i, y + CY - 34 + i, 0xFFFFFFFF);
        g.fill(x + CX - 1, y + CY - 36, x + CX + 2, y + CY - 35, 0xFFFF50E6);

        // окошко цели
        boolean hoverTarget = isOver(mouseX, mouseY, x + TX - 1, y + TY - 1, 18, 18);
        if (hoverTarget) g.fill(x + TX - 1, y + TY - 1, x + TX + 17, y + TY + 17, 0x60FFFFFF);
        Item target = menu.target();
        if (target != null) {
            ItemStack st = new ItemStack(target);
            g.renderItem(st, x + TX, y + TY);
            int n = Math.max(1, menu.prize());
            g.renderItemDecorations(font, st, x + TX, y + TY, n > 1 ? String.valueOf(n) : null);
        } else {
            g.drawCenteredString(font, "?", x + TX + 8, y + TY + 4, 0xFF50E6FF);
        }
    }

    private int bulb(int i) {
        if (showingResult() && menu.result() == 1 && menu.secret() > 0) {
            int[] rainbow = {0xFFFFD700, 0xFFFF7A00, 0xFFFF50E6, 0xFF50E6FF, 0xFF3CFF7A};
            return rainbow[Math.floorMod(i + ticks, rainbow.length)];
        }
        if (showingResult()) return menu.result() == 1 ? (i % 2 == 0 ? 0xFF3CFF7A : 0xFFB6FFCF) : (i % 2 == 0 ? 0xFFFF3B3B : 0xFF701818);
        return switch (Math.floorMod(i, 3)) {
            case 0 -> 0xFFFFE14A;
            case 1 -> 0xFFFF50E6;
            default -> 0xFF50E6FF;
        };
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(font, title, imageWidth / 2, 6, 0xFFFFE14A);
        g.drawCenteredString(font, Component.translatable("gui.jackpotind.stake"), 30, 20, 0xFFFFC828);
        g.drawCenteredString(font, Component.translatable("gui.jackpotind.target"), TX + 8, 20, 0xFF50E6FF);
        g.drawString(font, Component.translatable("gui.jackpotind.boosts"), 4, 54, 0xFFFF9CF0, false);
        if (percentMode()) {
            g.drawCenteredString(font, menu.percent() + "%", 148, 52, 0xFF3CFF7A);
        } else {
            g.drawCenteredString(font, "x" + menu.count(), 148, 52, 0xFFFFFFFF);
        }

        // в центре колеса — шанс, после прокрутки — результат
        Component center;
        if (showingResult()) {
            center = menu.result() == 1 && menu.secret() > 0
                    ? Component.translatable("gui.jackpotind.secret", menu.secret()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    : menu.result() == 1
                    ? Component.translatable("gui.jackpotind.win").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    : Component.translatable("gui.jackpotind.lose").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            g.pose().pushPose();
            g.pose().translate(CX, CY + 36, 0);
            g.drawCenteredString(font, center, 0, 0, 0xFFFFFFFF);
            g.pose().popPose();
        }
        String pct = animating() ? "..." : String.format(Locale.ROOT, "%.0f%%", menu.chance() * 100);
        if (!animating() && menu.chance() > 0 && menu.chance() < 0.01) pct = "<1%";
        if (!animating() && percentMode() && menu.hasStake() && menu.target() != null && menu.prize() < 1) pct = Component.translatable("gui.jackpotind.low").getString();
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        g.drawCenteredString(font, pct, CX, CY - 4, 0xFFFFFFFF);
        g.pose().popPose();

        if (minecraft != null && minecraft.level != null && !showingResult()) {
            Component values = Component.translatable("gui.jackpotind.values",
                    ItemValues.format(menu.stakeValue(minecraft.level)), ItemValues.format(menu.targetValue(minecraft.level)));
            g.drawCenteredString(font, values, CX, 86, 0xFFE0D0FF);
        }
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFFB0A0C8, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (catalogOpen) {
            renderCatalog(g, mouseX, mouseY, partialTick);
            return;
        }
        renderTooltip(g, mouseX, mouseY);
        if (isOver(mouseX, mouseY, leftPos + TX - 1, topPos + TY - 1, 18, 18)) {
            List<Component> lines = new ArrayList<>();
            Item target = menu.target();
            if (target != null && minecraft != null && minecraft.level != null) {
                lines.add(target.getDescription());
                lines.add(Component.translatable("gui.jackpotind.value",
                        ItemValues.format(ItemValues.get(minecraft.level, target))).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("gui.jackpotind.pick_target").withStyle(ChatFormatting.AQUA));
            if (menu.chosenTarget() != null) {
                lines.add(Component.translatable("gui.jackpotind.clear_target").withStyle(ChatFormatting.GRAY));
            } else if (percentMode()) {
                lines.add(Component.translatable("gui.jackpotind.own_item").withStyle(ChatFormatting.GREEN));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            int idx = menu.slots.indexOf(hoveredSlot);
            if (idx >= UpgraderMenu.SLOT_BOOST && idx < UpgraderMenu.SIZE) {
                g.renderComponentTooltip(font, List.of(Component.translatable("gui.jackpotind.boost_hint")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)), mouseX, mouseY);
            } else if (idx == UpgraderMenu.SLOT_INPUT && percentMode()) {
                g.renderComponentTooltip(font, List.of(Component.translatable("gui.jackpotind.stake_hint").withStyle(ChatFormatting.GOLD),
                        Component.translatable("gui.jackpotind.percent_hint").withStyle(ChatFormatting.GREEN)), mouseX, mouseY);
            } else if (idx == UpgraderMenu.SLOT_INPUT) {
                g.renderComponentTooltip(font, List.of(Component.translatable("gui.jackpotind.stake_hint")
                        .withStyle(ChatFormatting.GOLD)), mouseX, mouseY);
            }
        }
    }

    private void renderCatalog(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = leftPos, y = topPos;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF5140A22);
        g.renderOutline(x, y, imageWidth, imageHeight, 0xFF50E6FF);
        search.render(g, mouseX, mouseY, partialTick);
        boolean hoverClose = isOver(mouseX, mouseY, x + 154, y + 5, 14, 14);
        g.drawCenteredString(font, "X", x + 161, y + 8, hoverClose ? 0xFFFF5050 : 0xFFFFFFFF);

        Item hovered = null;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scrollRow + r) * COLS + c;
                if (idx >= filtered.size()) break;
                int ix = x + 7 + c * CELL, iy = y + 24 + r * CELL;
                boolean over = isOver(mouseX, mouseY, ix, iy, CELL, CELL);
                g.fill(ix, iy, ix + CELL - 1, iy + CELL - 1, over ? 0xFF6A3A9A : 0xFF261436);
                g.renderItem(new ItemStack(filtered.get(idx)), ix + 1, iy + 1);
                if (over) hovered = filtered.get(idx);
            }
        }
        int max = maxScroll();
        if (max > 0) {
            int trackH = ROWS * CELL - 2;
            int thumbH = Math.max(10, trackH * ROWS / (ROWS + max));
            int thumbY = y + 24 + (trackH - thumbH) * scrollRow / max;
            g.fill(x + 170, y + 24, x + 172, y + 24 + trackH, 0xFF261436);
            g.fill(x + 170, thumbY, x + 172, thumbY + thumbH, 0xFF50E6FF);
        }
        if (filtered.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.jackpotind.nothing"), x + 88, y + 90, 0xFF888888);
        }
        if (hovered != null && minecraft != null && minecraft.level != null) {
            g.renderComponentTooltip(font, List.of(hovered.getDescription(),
                    Component.translatable("gui.jackpotind.value",
                            ItemValues.format(ItemValues.get(minecraft.level, hovered))).withStyle(ChatFormatting.GRAY)),
                    mouseX, mouseY);
        }
        g.pose().popPose();
    }

    // ---------------- ввод ----------------
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (catalogOpen) {
            int x = leftPos, y = topPos;
            if (isOver(mx, my, x + 154, y + 5, 14, 14)) { catalogOpen = false; return true; }
            if (search.mouseClicked(mx, my, button)) { search.setFocused(true); return true; }
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int idx = (scrollRow + r) * COLS + c;
                    if (idx >= filtered.size()) break;
                    if (isOver(mx, my, x + 7 + c * CELL, y + 24 + r * CELL, CELL, CELL)) {
                        UpgraderNetwork.CHANNEL.sendToServer(new UpgraderNetwork.SetTarget(menu.containerId,
                                BuiltInRegistries.ITEM.getKey(filtered.get(idx))));
                        catalogOpen = false;
                        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.5f);
                        return true;
                    }
                }
            }
            return true;
        }
        if (isOver(mx, my, leftPos + TX - 1, topPos + TY - 1, 18, 18) && !menu.isSpinning() && button == 1) {
            press(UpgraderMenu.BTN_CLEAR_TARGET);
            return true;
        }
        if (isOver(mx, my, leftPos + TX - 1, topPos + TY - 1, 18, 18) && !menu.isSpinning()) {
            catalogOpen = true;
            search.setFocused(true);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (catalogOpen) return true;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (catalogOpen) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (catalogOpen) {
            int step = hasShiftDown() ? ROWS : 1;
            scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(delta) * step));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (catalogOpen) {
            if (key == 256) { catalogOpen = false; return true; }
            search.keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (catalogOpen) return search.charTyped(c, modifiers);
        return super.charTyped(c, modifiers);
    }
}
