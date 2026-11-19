package com.jackpotind.client;

import com.jackpotind.drill.DrillNetwork;
import com.jackpotind.drill.DrillSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Настройки Тоннельного бура: какие блоки ломать, размер тоннеля, режим, добыча и прочее. */
public class DrillSettingsScreen extends Screen {
    private static final int W = 320, H = 222;
    private static final int COLS = 14, ROWS = 8, CELL = 18;

    private final InteractionHand hand;
    private final ItemStack stack;
    private final DrillSettings s;

    private int left, top;
    private boolean picker;
    private EditBox search;
    private final List<Block> allBlocks = new ArrayList<>();
    private final List<Block> filtered = new ArrayList<>();
    private int scroll;

    public DrillSettingsScreen(InteractionHand hand, ItemStack stack) {
        super(Component.translatable("gui.jackpotind.drill.title"));
        this.hand = hand;
        this.stack = stack;
        this.s = DrillSettings.of(stack);
    }

    @Override
    protected void init() {
        clearWidgets();
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (picker) initPicker(); else initMain();
    }

    // ---------------- главная страница ----------------
    private void initMain() {
        int x = left + 10, y = top + 30;
        // категории блоков: две колонки
        DrillSettings.Category[] cats = DrillSettings.Category.values();
        for (int i = 0; i < cats.length; i++) {
            DrillSettings.Category c = cats[i];
            int bx = x + (i % 2) * 74, by = y + (i / 2) * 22;
            addRenderableWidget(Button.builder(catText(c), b -> { s.toggle(c); b.setMessage(catText(c)); })
                    .bounds(bx, by, 72, 20).build());
        }
        int yAfter = y + ((cats.length + 1) / 2) * 22;
        addRenderableWidget(Button.builder(allText(), b -> { s.allBlocks = !s.allBlocks; b.setMessage(allText()); })
                .bounds(x, yAfter, 146, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.jackpotind.drill.custom", s.custom.size()),
                b -> { picker = true; init(); }).bounds(x, yAfter + 22, 146, 20).build());

        // правая колонка: размеры и режимы
        int rx = left + 164;
        addRenderableWidget(new IntSlider(rx, y, 146, "gui.jackpotind.drill.length", DrillSettings.MIN_LENGTH,
                DrillSettings.MAX_LENGTH, s.length, v -> s.length = v));
        addRenderableWidget(new IntSlider(rx, y + 22, 146, "gui.jackpotind.drill.width", 1, DrillSettings.MAX_SIZE, s.width, v -> s.width = v));
        addRenderableWidget(new IntSlider(rx, y + 44, 146, "gui.jackpotind.drill.height", 1, DrillSettings.MAX_SIZE, s.height, v -> s.height = v));
        addRenderableWidget(Button.builder(modeText(), b -> {
            s.mode = DrillSettings.Mode.values()[(s.mode.ordinal() + 1) % DrillSettings.Mode.values().length];
            b.setMessage(modeText());
        }).bounds(rx, y + 66, 146, 20).build());
        addRenderableWidget(Button.builder(dropsText(), b -> {
            s.drops = DrillSettings.Drops.values()[(s.drops.ordinal() + 1) % DrillSettings.Drops.values().length];
            b.setMessage(dropsText());
        }).bounds(rx, y + 88, 146, 20).build());
        addRenderableWidget(Button.builder(flag("fluids", s.removeFluids), b -> { s.removeFluids = !s.removeFluids; b.setMessage(flag("fluids", s.removeFluids)); })
                .bounds(rx, y + 110, 146, 20).build());
        addRenderableWidget(Button.builder(flag("protect", s.protectContainers), b -> { s.protectContainers = !s.protectContainers; b.setMessage(flag("protect", s.protectContainers)); })
                .bounds(rx, y + 132, 72, 20).build());
        addRenderableWidget(Button.builder(flag("sound", s.sound), b -> { s.sound = !s.sound; b.setMessage(flag("sound", s.sound)); })
                .bounds(rx + 74, y + 132, 72, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.jackpotind.drill.save").withStyle(ChatFormatting.GREEN),
                b -> save()).bounds(left + 10, top + H - 28, 146, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(left + 164, top + H - 28, 146, 20).build());
    }

    private Component catText(DrillSettings.Category c) {
        boolean on = s.has(c);
        return Component.literal(on ? "✔ " : "✘ ").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                .append(Component.translatable("drill.jackpotind.cat." + c.key).withStyle(on ? ChatFormatting.WHITE : ChatFormatting.GRAY));
    }

    private Component allText() {
        return Component.translatable(s.allBlocks ? "gui.jackpotind.drill.all_on" : "gui.jackpotind.drill.all_off")
                .withStyle(s.allBlocks ? ChatFormatting.RED : ChatFormatting.GRAY);
    }

    private Component modeText() {
        return Component.translatable("gui.jackpotind.drill.mode",
                Component.translatable("drill.jackpotind.mode." + s.mode.name().toLowerCase(Locale.ROOT)));
    }

    private Component dropsText() {
        return Component.translatable("gui.jackpotind.drill.drops",
                Component.translatable("drill.jackpotind.drops." + s.drops.name().toLowerCase(Locale.ROOT)));
    }

    private Component flag(String key, boolean on) {
        return Component.translatable("gui.jackpotind.drill." + key).append(": ")
                .append(Component.translatable(on ? "options.on" : "options.off").withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private void save() {
        s.clamp();
        s.writeTo(stack); // сразу видно в подсказке; сервер сохранит то же самое
        DrillNetwork.CHANNEL.sendToServer(new DrillNetwork.Save(hand == InteractionHand.MAIN_HAND, s.save()));
        onClose();
    }

    // ---------------- выбор своих блоков ----------------
    private void initPicker() {
        if (allBlocks.isEmpty()) {
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b.asItem() != Items.AIR) allBlocks.add(b);
            }
        }
        search = new EditBox(font, left + 10, top + 24, W - 20, 14, Component.empty());
        search.setHint(Component.translatable("gui.jackpotind.drill.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setResponder(t -> refilter());
        addRenderableWidget(search);
        setInitialFocus(search);
        addRenderableWidget(Button.builder(Component.translatable("gui.jackpotind.drill.clear"), b -> s.custom.clear())
                .bounds(left + 10, top + H - 28, 146, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> { picker = false; init(); })
                .bounds(left + 164, top + H - 28, 146, 20).build());
        refilter();
    }

    private void refilter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        // выбранные — первыми
        for (Block b : allBlocks) if (s.custom.contains(key(b)) && matches(b, q)) filtered.add(b);
        for (Block b : allBlocks) if (!s.custom.contains(key(b)) && matches(b, q)) filtered.add(b);
        scroll = 0;
    }

    private static boolean matches(Block b, String q) {
        return q.isEmpty() || b.getName().getString().toLowerCase(Locale.ROOT).contains(q) || key(b).toString().contains(q);
    }

    private static ResourceLocation key(Block b) { return BuiltInRegistries.BLOCK.getKey(b); }

    private int maxScroll() { return Math.max(0, (filtered.size() + COLS - 1) / COLS - ROWS); }

    private int gridX() { return left + (W - COLS * CELL) / 2; }
    private int gridY() { return top + 44; }

    // ---------------- отрисовка ----------------
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, 0xF0140A22);
        g.renderOutline(left, top, W, H, 0xFFFF50E6);
        g.renderOutline(left + 2, top + 2, W - 4, H - 4, 0xFF50E6FF);
        g.drawCenteredString(font, title, width / 2, top + 9, 0xFFFFE14A);

        if (!picker) {
            g.drawString(font, Component.translatable("gui.jackpotind.drill.blocks"), left + 10, top + 20, 0xFFFF9CF0, false);
            g.drawString(font, Component.translatable("gui.jackpotind.drill.tunnel"), left + 164, top + 20, 0xFF50E6FF, false);
            int volume = s.length * s.width * s.height;
            g.drawCenteredString(font, Component.translatable("gui.jackpotind.drill.summary", s.width, s.height, s.length, volume)
                    .withStyle(ChatFormatting.GRAY), width / 2, top + H - 40, 0xFFAAAAAA);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        super.render(g, mouseX, mouseY, partialTick);
        Block hovered = null;
        int gx = gridX(), gy = gridY();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scroll + r) * COLS + c;
                if (idx >= filtered.size()) break;
                Block b = filtered.get(idx);
                int ix = gx + c * CELL, iy = gy + r * CELL;
                boolean sel = s.custom.contains(key(b));
                boolean over = mouseX >= ix && mouseX < ix + CELL && mouseY >= iy && mouseY < iy + CELL;
                g.fill(ix, iy, ix + CELL - 1, iy + CELL - 1, sel ? 0xFF1F8A45 : over ? 0xFF5A3A80 : 0xFF261436);
                g.renderItem(new ItemStack(b.asItem()), ix + 1, iy + 1);
                if (over) hovered = b;
            }
        }
        g.drawCenteredString(font, Component.translatable("gui.jackpotind.drill.picked", s.custom.size()).withStyle(ChatFormatting.GREEN),
                width / 2, gy + ROWS * CELL + 2, 0xFFFFFFFF);
        if (hovered != null) {
            boolean sel = s.custom.contains(key(hovered));
            g.renderComponentTooltip(font, List.of(hovered.getName(),
                    Component.translatable(sel ? "gui.jackpotind.drill.click_remove" : "gui.jackpotind.drill.click_add")
                            .withStyle(sel ? ChatFormatting.RED : ChatFormatting.GREEN)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (picker) {
            int gx = gridX(), gy = gridY();
            if (mx >= gx && mx < gx + COLS * CELL && my >= gy && my < gy + ROWS * CELL) {
                int idx = (scroll + (int) ((my - gy) / CELL)) * COLS + (int) ((mx - gx) / CELL);
                if (idx < filtered.size()) {
                    ResourceLocation k = key(filtered.get(idx));
                    if (!s.custom.remove(k)) s.custom.add(k);
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (picker) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(delta) * (hasShiftDown() ? ROWS : 1)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (picker && key == 256) { picker = false; init(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    /** Ползунок целых чисел. */
    private static final class IntSlider extends AbstractSliderButton {
        private final String key;
        private final int min, max;
        private final IntConsumer onChange;

        IntSlider(int x, int y, int w, String key, int min, int max, int value, IntConsumer onChange) {
            super(x, y, w, 20, Component.empty(), (value - min) / (double) (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        private int current() { return min + (int) Math.round(value * (max - min)); }

        @Override
        protected void updateMessage() { setMessage(Component.translatable(key, current())); }

        @Override
        protected void applyValue() { onChange.accept(current()); }
    }
}
