package com.absolutum.client;

import com.absolutum.upgrader.ItemValues;
import com.absolutum.upgrader.UpgraderMenu;
import com.absolutum.upgrader.UpgraderNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UpgraderScreen extends AbstractContainerScreen<UpgraderMenu> {
    private static final int COLS = 9, ROWS = 9, CELL = 18;
    private static final int TX = 152, TY = 28;               // окошко цели
    private static final int BAR_X = 8, BAR_Y = 84, BAR_W = 160, BAR_H = 8;

    private Button spinButton;
    private EditBox search;
    private boolean catalogOpen;
    private List<Item> allItems = List.of();
    private final List<Item> filtered = new ArrayList<>();
    private int scrollRow;

    private int ticks;
    private int lastSpinId = 0;
    private int animStart = -1000;

    public UpgraderScreen(UpgraderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 190;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        spinButton = addRenderableWidget(Button.builder(Component.translatable("gui.absolutum.upgrader.spin"),
                b -> press(UpgraderMenu.BTN_SPIN)).bounds(leftPos + 70, topPos + 58, 50, 16).build());
        addRenderableWidget(Button.builder(Component.literal("-"),
                b -> press(hasShiftDown() ? UpgraderMenu.BTN_MINUS10 : UpgraderMenu.BTN_MINUS))
                .bounds(leftPos + 124, topPos + 60, 12, 12).build());
        addRenderableWidget(Button.builder(Component.literal("+"),
                b -> press(hasShiftDown() ? UpgraderMenu.BTN_PLUS10 : UpgraderMenu.BTN_PLUS))
                .bounds(leftPos + 158, topPos + 60, 12, 12).build());

        search = new EditBox(font, leftPos + 8, topPos + 6, 140, 12, Component.empty());
        search.setMaxLength(50);
        search.setHint(Component.translatable("gui.absolutum.upgrader.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(s -> refilter());
        allItems = BuiltInRegistries.ITEM.stream()
                .filter(i -> i != Items.AIR && !new ItemStack(i).is(ItemValues.BLACKLIST))
                .toList();
        refilter();
        lastSpinId = menu.spinId();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void refilter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Item item : allItems) {
            if (q.isEmpty()
                    || item.getDescription().getString().toLowerCase(Locale.ROOT).contains(q)
                    || BuiltInRegistries.ITEM.getKey(item).toString().contains(q)) {
                filtered.add(item);
            }
        }
        scrollRow = 0;
    }

    private int maxScroll() {
        return Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS);
    }

    private boolean animating() {
        return ticks - animStart < UpgraderMenu.SPIN_TICKS;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        search.tick();
        if (menu.spinId() != lastSpinId) {
            lastSpinId = menu.spinId();
            animStart = ticks;
        }
        spinButton.active = !menu.isSpinning() && !animating() && menu.chance() > 0;
    }

    private static boolean isOver(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------- отрисовка ----------------
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF1B1328);
        g.renderOutline(x, y, imageWidth, imageHeight, 0xFFFFC828);

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int color = i == UpgraderMenu.SLOT_INPUT ? 0xFFFFC828 : i < UpgraderMenu.SIZE ? 0xFFFF50E6 : 0xFF3A2D52;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, color);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF0C0814);
        }

        // окошко цели
        boolean hoverTarget = isOver(mouseX, mouseY, x + TX - 1, y + TY - 1, 18, 18);
        g.fill(x + TX - 1, y + TY - 1, x + TX + 17, y + TY + 17, hoverTarget ? 0xFFFFFFFF : 0xFF50C8FF);
        g.fill(x + TX, y + TY, x + TX + 16, y + TY + 16, 0xFF0C0814);
        Item target = menu.target();
        if (target != null) {
            ItemStack st = new ItemStack(target, menu.count());
            g.renderItem(st, x + TX, y + TY);
            g.renderItemDecorations(font, st, x + TX, y + TY);
        } else {
            g.drawCenteredString(font, "?", x + TX + 8, y + TY + 4, 0xFF50C8FF);
        }

        // шкала шанса
        int bx = x + BAR_X, by = y + BAR_Y;
        g.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, 0xFF000000);
        g.fill(bx, by, bx + BAR_W, by + BAR_H, 0xFF6A1E24);
        int green = (int) Math.round(menu.chance() * BAR_W);
        g.fill(bx, by, bx + green, by + BAR_H, 0xFF2ECC55);
        if (menu.result() != 0) {
            float t = Math.min(1f, (ticks - animStart + partialTick) / UpgraderMenu.SPIN_TICKS);
            // бегунок пробегает шкалу 3 раза и замедляется к точке выпадения
            double eased = 1 - Math.pow(1 - t, 3);
            double travel = (3 + menu.roll()) * eased;
            double pos = t >= 1f ? menu.roll() : travel - Math.floor(travel);
            int px = bx + (int) Math.round(pos * (BAR_W - 1));
            g.fill(px - 1, by - 4, px + 1, by + BAR_H + 4, 0xFFFFFFFF);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xFFFFC828, false);
        g.drawString(font, Component.translatable("gui.absolutum.upgrader.stake"), 8, 18, 0xFFE0D0FF, false);
        Component tl = Component.translatable("gui.absolutum.upgrader.target");
        g.drawString(font, tl, 168 - font.width(tl), 18, 0xFFE0D0FF, false);
        g.drawString(font, Component.translatable("gui.absolutum.upgrader.boosts"), 8, 50, 0xFFFF9CF0, false);

        if (minecraft != null && minecraft.level != null) {
            g.drawString(font, Component.translatable("gui.absolutum.upgrader.stake_value",
                    ItemValues.format(menu.stakeValue(minecraft.level))), 30, 27, 0xFFFFFFFF, false);
            g.drawString(font, Component.translatable("gui.absolutum.upgrader.target_value",
                    ItemValues.format(menu.targetValue(minecraft.level))), 30, 37, 0xFFFFFFFF, false);
        }

        Component center;
        if (menu.result() != 0 && animating()) {
            center = Component.translatable("gui.absolutum.upgrader.spinning").withStyle(ChatFormatting.YELLOW);
        } else if (menu.result() != 0 && ticks - animStart < UpgraderMenu.SPIN_TICKS + 60) {
            center = menu.result() == 1
                    ? Component.translatable("gui.absolutum.upgrader.success").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    : Component.translatable("gui.absolutum.upgrader.failure").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        } else {
            double c = menu.chance();
            ChatFormatting color = c >= 0.5 ? ChatFormatting.GREEN : c >= 0.15 ? ChatFormatting.YELLOW : ChatFormatting.RED;
            center = Component.translatable("gui.absolutum.upgrader.chance",
                    String.format(Locale.ROOT, "%.2f%%", c * 100)).withStyle(color);
        }
        g.drawCenteredString(font, center, 100, 48, 0xFFFFFFFF);
        g.drawCenteredString(font, "x" + menu.count(), 147, 62, 0xFFFFFFFF);
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
            if (target != null) {
                lines.add(target.getDescription());
                if (minecraft != null && minecraft.level != null) {
                    lines.add(Component.translatable("gui.absolutum.upgrader.value",
                            ItemValues.format(ItemValues.get(minecraft.level, target))).withStyle(ChatFormatting.GRAY));
                }
            }
            lines.add(Component.translatable("gui.absolutum.upgrader.pick_target").withStyle(ChatFormatting.AQUA));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            int idx = menu.slots.indexOf(hoveredSlot);
            if (idx >= UpgraderMenu.SLOT_BOOST && idx < UpgraderMenu.SIZE) {
                g.renderComponentTooltip(font, List.of(Component.translatable("gui.absolutum.upgrader.boost_hint")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)), mouseX, mouseY);
            } else if (idx == UpgraderMenu.SLOT_INPUT) {
                g.renderComponentTooltip(font, List.of(Component.translatable("gui.absolutum.upgrader.stake_hint")
                        .withStyle(ChatFormatting.GOLD)), mouseX, mouseY);
            }
        }
    }

    private void renderCatalog(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = leftPos, y = topPos;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xF5120A1E);
        g.renderOutline(x, y, imageWidth, imageHeight, 0xFF50C8FF);
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
                g.fill(ix, iy, ix + CELL - 1, iy + CELL - 1, over ? 0xFF5A4A7A : 0xFF241A36);
                g.renderItem(new ItemStack(filtered.get(idx)), ix + 1, iy + 1);
                if (over) hovered = filtered.get(idx);
            }
        }
        int max = maxScroll();
        if (max > 0) {
            int trackH = ROWS * CELL - 2;
            int thumbH = Math.max(10, trackH * ROWS / (ROWS + max));
            int thumbY = y + 24 + (trackH - thumbH) * scrollRow / max;
            g.fill(x + 170, y + 24, x + 172, y + 24 + trackH, 0xFF241A36);
            g.fill(x + 170, thumbY, x + 172, thumbY + thumbH, 0xFF50C8FF);
        }
        if (filtered.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("gui.absolutum.upgrader.nothing"), x + 88, y + 90, 0xFF888888);
        }
        if (hovered != null && minecraft != null && minecraft.level != null) {
            g.renderComponentTooltip(font, List.of(hovered.getDescription(),
                    Component.translatable("gui.absolutum.upgrader.value",
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
                        return true;
                    }
                }
            }
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
            if (key == 256) { catalogOpen = false; return true; } // Esc закрывает только каталог
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
