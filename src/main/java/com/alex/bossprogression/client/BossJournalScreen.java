package com.alex.bossprogression.client;

import com.alex.bossprogression.boss.BossDefinition;
import com.alex.bossprogression.boss.BossProgressData;
import com.alex.bossprogression.boss.BossRegistry;
import com.alex.bossprogression.boss.BossState;
import com.alex.bossprogression.condition.BossCondition;
import com.alex.bossprogression.condition.KillCondition;
import com.alex.bossprogression.condition.KillTagCondition;
import com.alex.bossprogression.condition.ActivityCondition;
import com.alex.bossprogression.condition.ChestLootedCondition;
import com.alex.bossprogression.network.RequestItemSubmission;
import com.alex.bossprogression.network.RequestBossReset;
import com.alex.bossprogression.registry.ModAttachments;
import com.alex.bossprogression.reward.BossReward;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Vanilla parchment spread with a boss index and server-authoritative encounter details. */
public final class BossJournalScreen extends Screen {
    private static final ResourceLocation LEFT_PAGE = ResourceLocation.fromNamespaceAndPath(
            "bossprogression", "textures/gui/book_left.png");
    private static final int TEXT_WIDTH = 114;
    private static final float TEXT_SCALE = 0.8F;
    private static final int PAGE_LINES = 12;
    private static final int LINE_HEIGHT = 8;
    private static final int LIST_ROWS = 4;
    private static final int LIST_ROW_HEIGHT = 24;
    private static final int INK = 0xFF201810;
    private static final int GREEN = 0xFF23752C;
    private static final int RED = 0xFFAA2929;
    private record JournalLine(FormattedCharSequence text, int indent, Boolean complete) {}
    private int selected, listPage, detailPage;
    private int left, top, right;
    private List<BossDefinition> bosses = List.of();
    private List<JournalLine> lines = List.of();
    private boolean canReset;
    private PageButton listBack, listNext, detailBack, detailNext;
    private InkButton reset, submit;

    private BossJournalScreen() {
        super(Component.translatable("screen.bossprogression.journal"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new BossJournalScreen());
    }

    @Override
    protected void init() {
        left = (width - 292) / 2;
        right = left + 146;
        top = Math.max(2, (height - 180) / 2);
        listBack = addRenderableWidget(new PageButton(left + 18, top + 158, false, b -> changeList(-1), true));
        listNext = addRenderableWidget(new PageButton(left + 106, top + 158, true, b -> changeList(1), true));
        detailBack = addRenderableWidget(new PageButton(right + 18, top + 158, false, b -> changeDetails(-1), true));
        detailNext = addRenderableWidget(new PageButton(right + 106, top + 158, true, b -> changeDetails(1), true));
        reset = addRenderableWidget(new InkButton(right + 20, top + 137, 106, 17, "Reset Progress", b -> {
            if (canReset && !bosses.isEmpty()) PacketDistributor.sendToServer(new RequestBossReset(bosses.get(selected).id()));
        }));
        submit = addRenderableWidget(new InkButton(right + 20, top + 137, 106, 17, "Submit Items", b -> {
            if (!bosses.isEmpty()) PacketDistributor.sendToServer(new RequestItemSubmission(bosses.get(selected).id()));
        }));
        addRenderableWidget(new InkButton(right + 123, top + 9, 12, 12, "x", b -> onClose()));
        refresh();
    }

    /** No post-process blur: the world is only darkened before rendering the book. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);
    }

    private void append(List<JournalLine> result, String text) {
        if (text.isEmpty()) result.add(new JournalLine(FormattedCharSequence.EMPTY, 0, null));
        else append(result, Component.literal(text));
    }

    private void append(List<JournalLine> result, Component text) {
        for (var line : font.split(text, (int) (TEXT_WIDTH / TEXT_SCALE))) result.add(new JournalLine(line, 0, null));
    }

    private void requirement(List<JournalLine> result, BossCondition condition, BossState state) {
        int progress = state == null ? 0 : state.progress(condition.id());
        boolean complete = progress >= condition.requiredCount();
        Component label = Component.empty().append(JournalLabels.condition(condition))
                .append(" (" + progress + " / " + condition.requiredCount() + ")");
        var wrapped = font.split(label, (int) ((TEXT_WIDTH - 9) / TEXT_SCALE));
        for (int i = 0; i < wrapped.size(); i++) result.add(new JournalLine(wrapped.get(i), 9, i == 0 ? complete : null));
    }

    private void heading(List<JournalLine> result, String key) {
        append(result, Component.translatable("journal.bossprogression." + key).withStyle(net.minecraft.ChatFormatting.BOLD));
    }

    private void refresh() {
        bosses = List.copyOf(BossRegistry.clientAll());
        selected = Math.max(0, Math.min(selected, bosses.size() - 1));
        listPage = Math.max(0, Math.min(listPage, Math.max(0, (bosses.size() - 1) / LIST_ROWS)));
        canReset = false;
        List<JournalLine> result = new ArrayList<>();
        if (bosses.isEmpty() || minecraft.player == null) append(result, "No encounters found.");
        else {
            BossDefinition boss = bosses.get(selected);
            BossState state = minecraft.player.getData(ModAttachments.BOSS_PROGRESS.get()).get(boss.id());
            append(result, Component.translatable("journal.bossprogression.status", statusLabel(state))
                    .withStyle(style -> style.withColor(statusColor(state) & 0xFFFFFF)));
            append(result, "");
            heading(result, "requirements");
            for (BossCondition condition : boss.conditions()) {
                requirement(result, condition, state);
                if (condition instanceof KillCondition kill && !kill.filters().description().isEmpty()) append(result, JournalLabels.filters(kill.filters()));
                if (condition instanceof KillTagCondition tag && !tag.filters().description().isEmpty()) append(result, JournalLabels.filters(tag.filters()));
                if (condition instanceof ChestLootedCondition chest) chest.lootTable().ifPresent(t -> append(result,
                        Component.translatable("journal.bossprogression.loot", JournalLabels.name("loot_table", t))));
            }
            if (state != null && state.dungeonGenerated() && !state.defeated()
                    && state.dungeonDimension() != null && state.dungeonPos() != null) {
                append(result, "");
                heading(result, "dungeon");
                append(result, JournalLabels.name("dimension", state.dungeonDimension().location()));
                append(result, state.dungeonPos().toShortString());
            }
            if (state != null && state.defeated()) {
                append(result, "");
                append(result, Component.translatable("journal.bossprogression.boss_defeated")
                        .withStyle(style -> style.withColor(RED & 0xFFFFFF)));
            }
            if (!boss.rewards().isEmpty()) {
                append(result, "");
                heading(result, "rewards");
                for (BossReward reward : boss.rewards()) append(result, JournalLabels.reward(reward));
            }
            canReset = boss.repeatable() && state != null && state.defeated() && state.rewardGranted();
        }
        lines = result;
        detailPage = Math.max(0, Math.min(detailPage, detailPages() - 1));
        listBack.visible = listPage > 0;
        listNext.visible = (listPage + 1) * LIST_ROWS < bosses.size();
        detailBack.visible = detailPage > 0;
        detailNext.visible = detailPage < detailPages() - 1;
        reset.visible = canReset;
        BossState selectedState = bosses.isEmpty() || minecraft.player == null ? null
                : minecraft.player.getData(ModAttachments.BOSS_PROGRESS.get()).get(bosses.get(selected).id());
        submit.visible = !bosses.isEmpty() && (selectedState == null || !selectedState.unlocked())
                && bosses.get(selected).conditions().stream().anyMatch(c -> c instanceof ActivityCondition a
                    && a.kind() == ActivityCondition.Kind.SUBMIT_ITEM
                    && (selectedState == null || selectedState.progress(a.id()) < a.requiredCount()));
    }

    private int detailPages() { return Math.max(1, (lines.size() + PAGE_LINES - 1) / PAGE_LINES); }
    private void changeDetails(int delta) { detailPage = Math.max(0, Math.min(detailPages() - 1, detailPage + delta)); }
    private void changeList(int delta) {
        listPage = Math.max(0, Math.min(Math.max(0, (bosses.size() - 1) / LIST_ROWS), listPage + delta));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        refresh();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        // Pre-mirrored vanilla parchment: negative pose scaling reverses winding and culls the quad.
        graphics.blit(LEFT_PAGE, left, top, 0, 0, 146, 180, 146, 180);
        graphics.blit(BookViewScreen.BOOK_LOCATION, right, top, 20, 0, 146, 180);
        text(graphics, title.getString(), left + 16, top + 16);
        graphics.fill(left + 16, top + 29, left + 130, top + 30, 0xFFA98758);
        graphics.fill(right + 16, top + 29, right + 130, top + 30, 0xFFA98758);
        BossProgressData data = minecraft.player == null ? null : minecraft.player.getData(ModAttachments.BOSS_PROGRESS.get());
        for (int row = 0; row < LIST_ROWS; row++) {
            int index = listPage * LIST_ROWS + row;
            if (index >= bosses.size()) break;
            BossDefinition boss = bosses.get(index);
            int y = top + 38 + row * LIST_ROW_HEIGHT;
            boolean hovered = mouseX >= left + 16 && mouseX < left + 130 && mouseY >= y - 1 && mouseY < y + 23;
            if (index == selected || hovered) graphics.fill(left + 14, y - 1, left + 132, y + 23,
                    index == selected ? 0x55BD914D : 0x22BD914D);
            BossState state = data == null ? null : data.get(boss.id());
            text(graphics, boss.displayName().getString(), left + 16, y, statusColor(state));
            compact(graphics, statusLabel(state).getVisualOrderText(), left + 16, y + 10, statusColor(state));
        }
        if (!bosses.isEmpty()) {
            BossDefinition boss = bosses.get(selected);
            BossState state = data == null ? null : data.get(boss.id());
            // Leave room for the book's close button.
            compact(graphics, Component.literal(font.plainSubstrByWidth(boss.displayName().getString(),
                    (int) (100 / TEXT_SCALE))).getVisualOrderText(), right + 16, top + 16, statusColor(state));
        }
        for (int i = 0; i < PAGE_LINES; i++) {
            int index = detailPage * PAGE_LINES + i;
            if (index >= lines.size()) break;
            JournalLine line = lines.get(index);
            int y = top + 38 + i * LINE_HEIGHT;
            if (line.complete() != null) marker(graphics, right + 16, y, line.complete());
            compact(graphics, line.text(), right + 16 + line.indent(), y, INK);
        }
        String number = (detailPage + 1) + " / " + detailPages();
        compact(graphics, Component.literal(number).getVisualOrderText(),
                right + 73 - Math.round(font.width(number) * TEXT_SCALE / 2), top + 161, INK);
        // Background was already drawn; calling Screen.render here would cover/blur the book again.
        listBack.render(graphics, mouseX, mouseY, partialTick);
        listNext.render(graphics, mouseX, mouseY, partialTick);
        detailBack.render(graphics, mouseX, mouseY, partialTick);
        detailNext.render(graphics, mouseX, mouseY, partialTick);
        reset.render(graphics, mouseX, mouseY, partialTick);
        for (var child : children()) {
            if (child instanceof InkButton button && button != reset) button.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void text(GuiGraphics graphics, String text, int x, int y) { text(graphics, text, x, y, INK); }
    private void text(GuiGraphics graphics, String text, int x, int y, int color) {
        compact(graphics, Component.literal(font.plainSubstrByWidth(text, (int) (TEXT_WIDTH / TEXT_SCALE)))
                .getVisualOrderText(), x, y, color);
    }
    private void compact(GuiGraphics graphics, FormattedCharSequence text, int x, int y, int color) {
        drawCompact(graphics, font, text, x, y, color);
    }
    private static void drawCompact(GuiGraphics graphics, net.minecraft.client.gui.Font font,
                                    FormattedCharSequence text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
    /** Draw pixel check/cross marks explicitly, independent of Unicode font coverage. */
    private static void marker(GuiGraphics graphics, int x, int y, boolean complete) {
        if (complete) {
            for (int i = 0; i < 3; i++) graphics.fill(x + i, y + 3 + i, x + i + 1, y + 4 + i, GREEN);
            for (int i = 0; i < 5; i++) graphics.fill(x + 2 + i, y + 5 - i, x + 3 + i, y + 6 - i, GREEN);
        } else {
            for (int i = 0; i < 5; i++) {
                graphics.fill(x + 1 + i, y + 1 + i, x + 2 + i, y + 2 + i, RED);
                graphics.fill(x + 5 - i, y + 1 + i, x + 6 - i, y + 2 + i, RED);
            }
        }
    }
    private static int statusColor(BossState state) { return state != null && state.defeated() ? RED : GREEN; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= left + 16 && mouseX < left + 130 && mouseY >= top + 37 && mouseY < top + 133) {
            int index = listPage * LIST_ROWS + ((int) mouseY - top - 37) / LIST_ROW_HEIGHT;
            if (index < bosses.size()) { selected = index; detailPage = 0; return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            if (mouseX < right) changeList(scrollY > 0 ? -1 : 1);
            else changeDetails(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) { changeDetails(1); return true; }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_PAGE_UP) { changeDetails(-1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static Component statusLabel(BossState state) {
        return Component.translatable("journal.bossprogression.status." + status(state).toLowerCase(java.util.Locale.ROOT).replace(' ', '_'));
    }

    private static String status(BossState state) {
        if (state != null && state.defeated()) return "DEFEATED";
        if (state == null || !state.unlocked()) return "LOCKED";
        return state.dungeonGenerated() ? "DUNGEON FOUND" : "READY";
    }

    /** Real clickable/narratable widget rendered as ink on parchment instead of a grey menu button. */
    private static final class InkButton extends Button {
        private InkButton(int x, int y, int width, int height, String label, OnPress press) {
            super(x, y, width, height, Component.translatable(switch (label) {
                case "Reset Progress" -> "journal.bossprogression.reset";
                case "Submit Items" -> "journal.bossprogression.submit";
                default -> "journal.bossprogression.close";
            }), press, DEFAULT_NARRATION);
        }
        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            var font = Minecraft.getInstance().font;
            int color = isHoveredOrFocused() ? 0xFF8E442D : INK;
            graphics.fill(getX(), getY() + getHeight() - 2, getX() + getWidth(), getY() + getHeight() - 1, color);
            drawCompact(graphics, font, getMessage().getVisualOrderText(),
                    getX() + Math.round((getWidth() - font.width(getMessage()) * TEXT_SCALE) / 2),
                    getY() + Math.round((getHeight() - font.lineHeight * TEXT_SCALE) / 2), color);
        }
    }
}
