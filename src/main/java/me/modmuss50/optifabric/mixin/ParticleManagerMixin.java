package me.modmuss50.optifabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.block.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;


@Pseudo
@Mixin(targets = "net/minecraft/client/particle/ParticleManager")
public abstract class ParticleManagerMixin {
	@Inject(method = "updateTerrainParticleColor", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private static void updateTerrainParticleColor(Particle particle, BlockState state, BlockRenderView world, BlockPos pos, @Coerce Object renderEnv, CallbackInfo call) {
		// guard this optifine function against calls with null `renderEnv`
		if (renderEnv == null) call.cancel();
	}
}
