package me.modmuss50.optifabric.compat.architectury.mixin;

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
@InterceptingMixin("dev/architectury/mixin/fabric/client/MixinGameRenderer")
abstract class GameRendererNew4erMixin {
	@Shim
	private native void renderScreenPre(float tickDelta, long startTime, boolean tick, CallbackInfo ci, int mouseX, int mouseY, Window window, Matrix4f matrix, MatrixStack matrices, DrawableHelper drawContext);

	@PlacatingSurrogate
	private void renderScreenPre(float tickDelta, long startTime, boolean tick, CallbackInfo ci, int mouseX, int mouseY, Window window, float guiFarPlane, Matrix4f matrix, MatrixStack matrices) {
	}

	@Inject(method = "render(FJZ)V", locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;renderWithTooltip(Lnet/minecraft/class_332;IIF)V", ordinal = 0))
	private void renderScreenPre(float tickDelta, long startTime, boolean tick, CallbackInfo ci, int mouseX, int mouseY, Window window, float guiFarPlane, Matrix4f matrix, MatrixStack matrices, float idk, DrawableHelper drawContext) {
		renderScreenPre(tickDelta, startTime, tick, ci, mouseX, mouseY, window, matrix, matrices, drawContext);
	}

	@Shim
	private native void renderScreenPost(float tickDelta, long startTime, boolean tick, CallbackInfo ci, int mouseX, int mouseY, Window window, Matrix4f matrix, MatrixStack matrices, DrawableHelper drawContext);

	@PlacatingSurrogate
	private void renderScreenPost(float tickDelta, long startTime, boolean tick, CallbackInfo ci, int mouseX, int mouseY, Window window, float guiFarPlane, Matrix4f matrix, MatrixStack matrices) {
	}

	@Inject(method = "render(FJZ)V", locals = LocalCapture.CAPTURE_FAILHARD,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;renderWithTooltip(Lnet/minecraft/class_332;IIF)V", shift = Shift.AFTER, ordinal = 0))
	private void renderScreenPost(float tickDelta, long startTime, boolean tick, CallbackInfo ci, int mouseX, int mouseY, Window window, float guiFarPlane, Matrix4f matrix, MatrixStack matrices, float idk, DrawableHelper drawContext) {
		renderScreenPost(tickDelta, startTime, tick, ci, mouseX, mouseY, window, matrix, matrices, drawContext);
	}
}