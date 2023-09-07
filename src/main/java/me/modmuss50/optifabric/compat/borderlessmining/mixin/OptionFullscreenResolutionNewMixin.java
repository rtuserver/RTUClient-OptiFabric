package me.modmuss50.optifabric.compat.borderlessmining.mixin;

import me.modmuss50.optifabric.compat.borderlessmining.MagicMixinBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.VideoOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Pseudo
@Mixin(targets = "net.optifine.gui.OptionFullscreenResolution", remap = false)
abstract class OptionFullscreenResolutionNewMixin {
	@ModifyArgs(method = "make",
				at = @At(value = "INVOKE", target = "Lnet/minecraft/class_7172;<init>(Ljava/lang/String;Lnet/minecraft/class_7172$class_7307;Lnet/minecraft/class_7172$class_7277;Lnet/minecraft/class_7172$class_7178;Ljava/lang/Object;Ljava/util/function/Consumer;)V", remap = true))
	private static void modifyOption(Args args) {
		Screen screen = MinecraftClient.getInstance().currentScreen;
		((MagicMixinBridge) (screen instanceof MagicMixinBridge ? screen : new VideoOptionsScreen(screen, MinecraftClient.getInstance().options))).optifabric£modifyFullscreenButton(args);
	}
}