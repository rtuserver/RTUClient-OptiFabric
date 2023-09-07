package me.modmuss50.optifabric.mod;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;

public class DrawContext {
    public static int drawTextWithShadow(TextRenderer textRenderer, Object matrices, String text, int x, int y, int color) {
        return textRenderer.drawWithShadow((MatrixStack) matrices, text, x, y, color);
    }
}
