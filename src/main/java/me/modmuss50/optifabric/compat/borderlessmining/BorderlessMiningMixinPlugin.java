package me.modmuss50.optifabric.compat.borderlessmining;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.fabricmc.tinyremapper.IMappingProvider.Member;

import me.modmuss50.optifabric.compat.InterceptingMixinPlugin;
import me.modmuss50.optifabric.util.RemappingUtils;

public class BorderlessMiningMixinPlugin extends InterceptingMixinPlugin {
	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		if ("VideoOptionsScreenMixin".equals(mixinInfo.getName())) {
			String init = RemappingUtils.getMethodName("class_446", "method_25426", "()V"); //VideoOptionsScreen, init

			for (MethodNode method : targetClass.methods) {
				if (init.equals(method.name) && "()V".equals(method.desc)) {
					Member createSimpleOption = RemappingUtils.mapMethod("class_7172", "<init>", getCreateSimpleOptionInitDescriptor());
					InsnList extra = new InsnList();
					LabelNode skip = new LabelNode();

					extra.add(new JumpInsnNode(Opcodes.GOTO, skip));
					extra.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, createSimpleOption.owner, createSimpleOption.name, createSimpleOption.desc, false));
					extra.add(skip);

					method.instructions.insertBefore(method.instructions.getFirst(), extra);
					break;
				}
			}
		}

		super.preApply(targetClassName, targetClass, mixinClassName, mixinInfo);
	}

	protected String getCreateSimpleOptionInitDescriptor() {
		return "(Ljava/lang/String;Lnet/minecraft/class_7172$class_7307;Lnet/minecraft/class_7172$class_7303;Lnet/minecraft/class_7172$class_7178;Ljava/lang/Object;Ljava/util/function/Consumer;)V";
	}
}