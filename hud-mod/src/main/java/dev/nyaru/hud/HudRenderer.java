package dev.nyaru.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class HudRenderer {
    private static final int PANEL_X = 4;
    private static final int LINE_H = 10;
    private static final int PADDING = 4;

    public static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getDebugHud().shouldShowDebugHud()) return;

        TextRenderer tr = client.textRenderer;
        int scaledHeight = client.getWindow().getScaledHeight();

        MutableText line1 = Text.literal("P ").formatted(Formatting.GOLD)
                .append(Text.literal(String.format("%,d", HudState.balance)).formatted(Formatting.YELLOW));
        MutableText line2 = Text.literal("Lv." + HudState.level + " ").formatted(Formatting.GREEN)
                .append(Text.literal(HudState.jobDisplay.isEmpty() ? "없음" : HudState.jobDisplay).formatted(Formatting.AQUA));
        MutableText line3 = buildXpText(HudState.xp, HudState.xpToNext, tr);

        int maxWidth = Math.max(tr.getWidth(line1), Math.max(tr.getWidth(line2), tr.getWidth(line3)));
        int panelW = maxWidth + PADDING * 2;
        int panelH = LINE_H * 3 + PADDING * 2;
        int panelY = scaledHeight - panelH - 30;

        context.fill(PANEL_X - 1, panelY - 1, PANEL_X + panelW + 1, panelY + panelH + 1, 0x55000000);

        context.drawText(tr, line1, PANEL_X + PADDING, panelY + PADDING, 0xFFFFFF, true);
        context.drawText(tr, line2, PANEL_X + PADDING, panelY + PADDING + LINE_H, 0xFFFFFF, true);
        context.drawText(tr, line3, PANEL_X + PADDING, panelY + PADDING + LINE_H * 2, 0xFFFFFF, true);
    }

    private static MutableText buildXpText(int xp, int xpToNext, TextRenderer tr) {
        int barLen = 10;
        float pct = xpToNext > 0 ? (float) xp / xpToNext : 0f;
        int filled = Math.max(0, Math.min(barLen, Math.round(pct * barLen)));
        int empty = barLen - filled;
        return Text.literal("█".repeat(filled)).formatted(Formatting.GREEN)
                .append(Text.literal("█".repeat(empty)).formatted(Formatting.DARK_GRAY))
                .append(Text.literal(" " + xp + "/" + xpToNext).formatted(Formatting.GRAY));
    }

    private HudRenderer() {}
}
