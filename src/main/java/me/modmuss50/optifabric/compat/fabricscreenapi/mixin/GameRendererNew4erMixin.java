package me.modmuss50.optifabric.compat.fabricscreenapi.mixin;

import me.modmuss50.optifabric.compat.InterceptingMixin;
import me.modmuss50.optifabric.compat.PlacatingSurrogate;
import me.modmuss50.optifabric.compat.Shim;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(GameRenderer.class)
@InterceptingMixin("net/fabricmc/fabric/mixin/screen/GameRendererMixin")
abstract class GameRendererNew4erMixin {
	@Shim
	private native void onBeforeRenderScreen(float tickDelta, long startTime, boolean tick, CallbackInfo call, int mouseX, int mouseY, MatrixStack matrixstack, DrawableHelper drawContext);

	@PlacatingSurrogate
	private void onBeforeRenderScreen(float tickDelta, long startTime, boolean tick, CallbackInfo call, int mouseX, int mouseY, Window window, float idk) {
	}

	@Inject(method = "render(FJZ)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;renderWithTooltip(Lnet/minecraft/class_332;IIF)V", ordinal = 0),
			locals = LocalCapture.CAPTURE_FAILHARD,
			cancellable = true)
	private void onBeforeRenderScreen(float tickDelta, long startTime, boolean tick, CallbackInfo call, int mouseX, int mouseY, Window window, float guiFarPlane, Matrix4f projection, MatrixStack matrixstack, float idk, DrawableHelper drawContext) {
		onBeforeRenderScreen(tickDelta, startTime, tick, call, mouseX, mouseY, matrixstack, drawContext);
	}

	@Shim
	private native void onAfterRenderScreen(float tickDelta, long startTime, boolean tick, CallbackInfo call, int mouseX, int mouseY, MatrixStack matrixstack, DrawableHelper drawContext);

	@PlacatingSurrogate
	private void onAfterRenderScreen(float tickDelta, long startTime, boolean tick, CallbackInfo call, int mouseX, int mouseY, Window window, float idk) {
	}

	@Inject(method = "render(FJZ)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;renderWithTooltip(Lnet/minecraft/class_332;IIF)V", shift = Shift.AFTER, ordinal = 0),
			locals = LocalCapture.CAPTURE_FAILHARD)
	private void onAfterRenderScreen(float tickDelta, long startTime, boolean tick, CallbackInfo call, int mouseX, int mouseY, Window window, float guiFarPlane, Matrix4f projection, MatrixStack matrixstack, float idk, DrawableHelper drawContext) {
		onAfterRenderScreen(tickDelta, startTime, tick, call, mouseX, mouseY, matrixstack, drawContext);
	}
}