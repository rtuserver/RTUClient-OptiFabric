package me.modmuss50.optifabric.mod;

import java.io.File;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.google.common.base.MoreObjects;

import org.apache.commons.lang3.tuple.Pair;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.util.version.SemanticVersionImpl;
import net.fabricmc.loader.util.version.SemanticVersionPredicateParser;

import me.modmuss50.optifabric.mod.OptifineVersion.JarType;
import me.modmuss50.optifabric.patcher.ClassCache;
import me.modmuss50.optifabric.util.RemappingUtils;

import com.chocohead.mm.api.ClassTinkerers;

public class OptifabricSetup implements Runnable {
	public static File optifineRuntimeJar = null;
	public static boolean usingScreenAPI;

	//This is called early on to allow us to get the transformers in beofore minecraft starts
	@Override
	public void run() {
		OptifineInjector injector;
		try {
			Pair<File, ClassCache> runtime = OptifineSetup.getRuntime();
			optifineRuntimeJar = runtime.getLeft();

			//Add the optifine jar to the classpath, as
			ClassTinkerers.addURL(runtime.getLeft().toURI().toURL());

			injector = new OptifineInjector(runtime.getRight());
			injector.setup();
		} catch (Throwable e) {
			if (!OptifabricError.hasError()) {
				OptifineVersion.jarType = JarType.INTERNAL_ERROR;
				OptifabricError.setError(e, "Failed to load OptiFine, please report this!\n\n" + e.getMessage());
			}
			System.err.println("Failed to setup optifine:");
			e.printStackTrace();
			return; //Avoid crashing out any other Fabric ASM users
		}

		BooleanSupplier particlesPresent = new FeatureFinder() {
			@Override
			protected boolean isPresent() {
				return injector.predictFuture(RemappingUtils.getClassName("class_702")).filter(node -> {//ParticleManager
					//(MatrixStack, VertexConsumerProvider$Immediate, LightmapTextureManager, Camera, Frustum)
					String desc = RemappingUtils.mapMethodDescriptor("(Lnet/minecraft/class_4587;Lnet/minecraft/class_4597$class_4598;"
							+ "Lnet/minecraft/class_765;Lnet/minecraft/class_4184;FLnet/minecraft/class_4604;)V");

					for (MethodNode method : node.methods) {
						if (("renderParticles".equals(method.name) || "render".equals(method.name)) && desc.equals(method.desc)) {
							return true;
						}
					}

					return false;
				}).isPresent();
			}
		};
		BooleanSupplier farPlanePresent = new FeatureFinder() {
			@Override
			protected boolean isPresent() {
				return injector.predictFuture(RemappingUtils.getClassName("class_757")).filter(node -> {//GameRenderer
					String render = RemappingUtils.getMethodName("class_757", "method_3192", "(FJZ)V");

					for (MethodNode method : node.methods) {
						if (render.equals(method.name) && "(FJZ)V".equals(method.desc)) {
							for (AbstractInsnNode insn : method.instructions) {
								if (insn.getType() == AbstractInsnNode.FIELD_INSN && "ForgeHooksClient_getGuiFarPlane".equals(((FieldInsnNode) insn).name)) {
									return true;
								}
							}

							break;
						}
					}

					return false;
				}).isPresent();
			}
		};
		BooleanSupplier setupFogPresent = new FeatureFinder() {
			@Override
			protected boolean isPresent() {
				return injector.predictFuture(RemappingUtils.getClassName("class_758")).filter(node -> {//BackgroundRenderer
					//(Camera, BackgroundRenderer$FogType)
					String desc = RemappingUtils.mapMethodDescriptor("(Lnet/minecraft/class_4184;Lnet/minecraft/class_758$class_4596;FZF)V");

					for (MethodNode method : node.methods) {
						if ("setupFog".equals(method.name) && desc.equals(method.desc)) {
							return true;
						}
					}

					return false;
				}).isPresent();
			}
		};

		if (isPresent("fabric-renderer-api-v1")) {
			if (isPresent("minecraft", ">=1.19")) {
				Mixins.addConfiguration("optifabric.compat.fabric-renderer-api.new-mixins.json");
			} else {
				Mixins.addConfiguration("optifabric.compat.fabric-renderer-api.mixins.json");
			}
		}

		if (isPresent("fabric-rendering-v1", ">=1.5.0") && particlesPresent.getAsBoolean()) {
			if (isPresent("minecraft", ">=1.19.3")) {
				Mixins.addConfiguration("optifabric.compat.fabric-rendering.new-mixins.json");
			} else {
				Mixins.addConfiguration("optifabric.compat.fabric-rendering.mixins.json");
			}
		}
		if (isPresent("fabric-rendering-v1", ">=1.13.0 <2.0") || isPresent("fabric-rendering-v1", ">=2.1.0")) {
			Mixins.addConfiguration("optifabric.compat.fabric-rendering.extra-mixins.json");
		}

		if (isPresent("fabric-rendering-data-attachment-v1")) {
			Mixins.addConfiguration("optifabric.compat.fabric-rendering-data.mixins.json");

			if (isPresent("fabric-rendering-data-attachment-v1", ">0.3.0")) {
				//0.43.1+ and with a patch to ChunkRendererRegionBuilder
				injector.predictFuture(RemappingUtils.getClassName("class_6850")).ifPresent(node -> {//ChunkRendererRegionBuilder, (World, BlockPos, BlockPos)ChunkRendererRegion
					String desc = RemappingUtils.mapMethodDescriptor("(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2338;IZ)Lnet/minecraft/class_853;");

					for (MethodNode method : node.methods) {
						if ("createRegion".equals(method.name) && desc.equals(method.desc)) {
							assert isPresent("minecraft", ">=1.18-rc.1");
							Mixins.addConfiguration("optifabric.compat.fabric-rendering-data.bonus-mixins.json");
							break;
						}
					}
				});
			} else if (isPresent("fabric-rendering-data-attachment-v1", ">0.2.0")) {
				//Below 0.43.1 and with a patch to ChunkRendererRegion
				injector.predictFuture(RemappingUtils.getClassName("class_853")).ifPresent(node -> {//ChunkRendererRegion, (World, BlockPos, BlockPos)ChunkRendererRegion
					String desc = RemappingUtils.mapMethodDescriptor("(Lnet/minecraft/class_1937;Lnet/minecraft/class_2338;Lnet/minecraft/class_2338;IZ)Lnet/minecraft/class_853;");

					for (MethodNode method : node.methods) {
						if ("generateCache".equals(method.name) && desc.equals(method.desc)) {
							assert isPresent("minecraft", ">=1.18-beta.1");
							Mixins.addConfiguration("optifabric.compat.fabric-rendering-data.extra-mixins.json");
							break;
						}
					}
				});
			}
		}

		if (isPresent("fabric-renderer-indigo")) {
			if (isPresent("minecraft", ">=1.19")) {
				injector.predictFuture(RemappingUtils.getClassName("class_776")).ifPresent(node -> {//BlockRenderManager
					String desc = RemappingUtils.getClassName("class_1921").concat(";)V"); //RenderLayer

					for (MethodNode method : node.methods) {
						if ("renderBatched".equals(method.name) && method.desc.endsWith(desc)) {
							Mixins.addConfiguration("optifabric.compat.indigo.newer-mixins.json");
							return;
						}
					}

					Mixins.addConfiguration("optifabric.compat.indigo.new-mixins.json");
				});
			} else {
				if (isPresent("fabric-renderer-indigo", ">=0.5.0")) {//First released in 0.51.0+1.18.2
					Mixins.addConfiguration("optifabric.compat.indigo.mixins.json");
				} else {
					Mixins.addConfiguration("optifabric.compat.indigo.old-mixins.json");
				}

				injector.predictFuture(RemappingUtils.getClassName("class_846$class_849")).ifPresent(node -> {//ChunkBuilder$ChunkData
					String nonEmptyLayers = RemappingUtils.mapFieldName("class_846$class_849", "field_4450", "Ljava/util/Set;");

					for (FieldNode field : node.fields) {
						if (nonEmptyLayers.equals(field.name) && "Ljava/util/Set;".equals(field.desc)) {
							return;
						}
					}

					Mixins.addConfiguration("optifabric.compat.indigo.extra-mixins.json");
				});
			}
		}

		if (isPresent("fabric-item-api-v1", ">=1.1.0") && isPresent("minecraft", "1.16.x")) {
			Mixins.addConfiguration("optifabric.compat.fabric-item-api.mixins.json");
		}

		if (isPresent("fabric-screen-api-v1")) {
			if (isPresent("minecraft", ">=1.20")) {
				Mixins.addConfiguration("optifabric.compat.fabric-screen-api.new4er-mixins.json");
			} else if (isPresent("fabric-api", ">=0.81.0")) {
				Mixins.addConfiguration("optifabric.compat.fabric-screen-api.new3er-mixins.json");
			} else if (isPresent("minecraft", ">=1.19.3")) {
				Mixins.addConfiguration("optifabric.compat.fabric-screen-api.newerer-mixins.json");
			} else if (isPresent("minecraft", ">=1.17-alpha.21.10.a")) {
				if (farPlanePresent.getAsBoolean()) {
					Mixins.addConfiguration("optifabric.compat.fabric-screen-api.newer-mixins.json");
				} else {
					Mixins.addConfiguration("optifabric.compat.fabric-screen-api.new-mixins.json");
				}
			} else {
				Mixins.addConfiguration("optifabric.compat.fabric-screen-api.mixins.json");
			}
			usingScreenAPI = true;
		}

		if (isPresent("fabric-lifecycle-events-v1", ">=1.4.6") && isPresent("minecraft", "1.17.x")) {
			Mixins.addConfiguration("optifabric.compat.fabric-lifecycle-events.mixins.json");
		} else if (isPresent("fabric-lifecycle-events-v1", ">=2.0.8")) {
			Mixins.addConfiguration("optifabric.compat.fabric-lifecycle-events.new-mixins.json");
		}

		Mixins.addConfiguration("optifabric.optifine.mixins.json");
		if (OptifabricSetup.isPresent("minecraft", "<=1.19.2")) {
			Mixins.addConfiguration("optifabric.optifine.old-mixins.json");
		}

		if (isPresent("fabricloader", ">=0.13.0") && (isPresent("cloth-client-events-v0", ">=3.1.58") || isPresent("cloth-client-events-v0", ">=2.1.60 <3.0") || isPresent("cloth-client-events-v0", ">=1.6.59 <2.0"))) {
			// no mixins are needed -- cloth had a workaround for https://github.com/FabricMC/Mixin/issues/80
			// but it is now fixed in fabricloader
		} else if (isPresent("cloth-client-events-v0", ">=2.0")) {
			if (farPlanePresent.getAsBoolean()) {
				Mixins.addConfiguration("optifabric.compat.cloth.newer-mixins.json");
			} else {
				Mixins.addConfiguration("optifabric.compat.cloth.new-mixins.json");
			}
		} else if (isPresent("cloth-client-events-v0")) {
			Mixins.addConfiguration("optifabric.compat.cloth.mixins.json");
		}

		if (isPresent("clothesline")) {
			Mixins.addConfiguration("optifabric.compat.clothesline.mixins.json");
		}

		if (isPresent("trumpet-skeleton")) {
			Mixins.addConfiguration("optifabric.compat.trumpet-skeleton.mixins.json");
		}

		if (isPresent("multiconnect", ">1.3.14 <1.6-beta.1")) {
			Mixins.addConfiguration("optifabric.compat.multiconnect.mixins.json");
		}

		if (isPresent("now-playing", ">=1.1.0")) {
			Mixins.addConfiguration("optifabric.compat.now-playing.mixins.json");
		}

		if (isPresent("origins", mod -> compareVersions(Pattern.compile("^1\\.16(\\.\\d)?-").matcher(mod.getVersion().getFriendlyString()).find() ? ">=1.16-0.2.0" : ">=0.4.1 <1.0", mod))) {
			if (isPresent("origins", mod -> !Pattern.compile("^1\\.16(\\.\\d)?-").matcher(mod.getVersion().getFriendlyString()).find() || compareVersions(">=1.16.3-0.4.0", mod))) {
				Mixins.addConfiguration("optifabric.compat.origins.mixins.json");
			}

			injector.predictFuture(RemappingUtils.getClassName("class_979")).ifPresent(node -> {//ElytraFeatureRenderer
				String desc = RemappingUtils.mapMethodDescriptor("(Lnet/minecraft/class_1799;Lnet/minecraft/class_1309;)Z"); //ItemStack, LivingEntity

				for (MethodNode method : node.methods) {
					if ("shouldRender".equals(method.name) && desc.equals(method.desc)) {
						Mixins.addConfiguration("optifabric.compat.origins.extra-mixins.json");
						break;
					}
				}
			});
		}


		if (isPresent("apoli", ">=2.3.1")) {
			Mixins.addConfiguration("optifabric.compat.apoli-newerer.mixins.json");

			if (setupFogPresent.getAsBoolean()) {
				Mixins.addConfiguration("optifabric.compat.apoli-newerer.extra-mixins.json");
			}
		} else if (isPresent("apoli", ">=2.2.2")) {
			Mixins.addConfiguration("optifabric.compat.apoli-newer.mixins.json");
		} else if (isPresent("apoli", ">=2.0")) {
			Mixins.addConfiguration("optifabric.compat.apoli-new.mixins.json");
		} else if (isPresent("apoli")) {
			Mixins.addConfiguration("optifabric.compat.apoli.mixins.json");
		}

		if (isPresent("additionalentityattributes") && setupFogPresent.getAsBoolean()) {
			Mixins.addConfiguration("optifabric.compat.additional-entity-attributes.mixins.json");
		}

		if (isPresent("staffofbuilding")) {
			Mixins.addConfiguration("optifabric.compat.staffofbuilding.mixins.json");
		}

		if (isPresent("sandwichable")) {
			if (isPresent("sandwichable", "<=1.2-rc4 >=1.2-alpha1")) {
				Mixins.addConfiguration("optifabric.compat.sandwichable.mixins.json");
			} else if (isPresent("sandwichable", "<1.2-alpha1")) {
				Mixins.addConfiguration("optifabric.compat.sandwichable-old.mixins.json");
			} else {//Versions like 1.3.a aren't SemVer, so won't hit either case above
				Mixins.addConfiguration("optifabric.compat.sandwichable.new-mixins.json");
			}
		}

		if (isPresent("astromine", "<1.6")) {//Only needed for the 1.16.1 versions
			Mixins.addConfiguration("optifabric.compat.astromine.mixins.json");
		}

		if (isPresent("carpet")) {
			if (!isPresent("minecraft", ">=1.17")) {
				Mixins.addConfiguration("optifabric.compat.carpet.mixins.json");
			}

			if (particlesPresent.getAsBoolean()) {
				if (isPresent("minecraft", ">=1.18.2")) {
					Mixins.addConfiguration("optifabric.compat.carpet.extra-new-mixins.json");
				} else {
					Mixins.addConfiguration("optifabric.compat.carpet.extra-mixins.json");
				}
			}
		}

		if (isPresent("hctm-base")) {
			Mixins.addConfiguration("optifabric.compat.hctm.mixins.json");
		}

		if (isPresent("mubble", "<4.0-pre5")) {
			Mixins.addConfiguration("optifabric.compat.mubble.mixins.json");
		}

		if (isPresent("dawn", ">=1.3 <=1.4")) {
			Mixins.addConfiguration("optifabric.compat.dawn.older-mixins.json");
		} else if (isPresent("dawn", ">1.4 <1.5")) {
			Mixins.addConfiguration("optifabric.compat.dawn.old-mixins.json");
		} else if (isPresent("dawn", ">=1.5 <1.8")) {
			Mixins.addConfiguration("optifabric.compat.dawn.mixins.json");
		}

		if (isPresent("phormat")) {
			Mixins.addConfiguration("optifabric.compat.phormat.mixins.json");
		}

		if (isPresent("chat_heads", "<0.2")) {
			Mixins.addConfiguration("optifabric.compat.chat-heads.mixins.json");
		}

		if (isPresent("mmorpg")) {
			Mixins.addConfiguration("optifabric.compat.age-of-exile.mixins.json");
		}

		if (isPresent("charm", ">=2.0 <2.1")) {
			Mixins.addConfiguration("optifabric.compat.charm-older.mixins.json");
		} else if (isPresent("charm", ">=2.1 <3.0")) {
			Mixins.addConfiguration("optifabric.compat.charm-old.mixins.json");

			if (isPresent("charm", ">=2.2.2")) {
				injector.predictFuture(RemappingUtils.getClassName("class_156")).ifPresent(node -> {//Util
					String desc = "(Lcom/mojang/datafixers/DSL$TypeReference;Ljava/lang/String;)Lcom/mojang/datafixers/types/Type;";
					String getChoiceTypeInternal = RemappingUtils.getMethodName("class_156", "method_29191", desc); //Util, getChoiceTypeInternal

					for (MethodNode method : node.methods) {
						if (getChoiceTypeInternal.equals(method.name) && desc.equals(method.desc)) {
							Mixins.addConfiguration("optifabric.compat.charm-plus.mixins.json");
							break;
						}
					}
				});
			}
		} else if (isPresent("charm", ">=3.0 <4.0")) {
			Mixins.addConfiguration("optifabric.compat.charm.mixins.json");
		} else if (isPresent("charm", ">=4.0")) {
			Mixins.addConfiguration("optifabric.compat.charm-new.mixins.json");
		}

		if (isPresent("voxelmap")) {
			Mixins.addConfiguration("optifabric.compat.voxelmap.mixins.json");
		}

		if (isPresent("appliedenergistics2")) {
			Mixins.addConfiguration("optifabric.compat.ae2.mixins.json");
		}

		if (isPresent("images", "=0.3.0")) {
			Mixins.addConfiguration("optifabric.compat.images-older.mixins.json");
		} else if (isPresent("images", ">=0.3.1 <1.0.1")) {
			Mixins.addConfiguration("optifabric.compat.images-old.mixins.json");
		} else if (isPresent("images", ">=1.0.1")) {
			Mixins.addConfiguration("optifabric.compat.images.mixins.json");
		}

		if (isPresent("architectury", ">=9.0.6")) {
			Mixins.addConfiguration("optifabric.compat.architectury-AB.new4er-mixins.json");
		} else if (isPresent("architectury", ">=7.0.52")) {
			Mixins.addConfiguration("optifabric.compat.architectury-AB.newererer-mixins.json");
		} else if (isPresent("architectury", ">=3.7")) {
			Mixins.addConfiguration("optifabric.compat.architectury-AB.newerer-mixins.json");
		} else if (isPresent("architectury", ">=2.0")) {
			assert isPresent("minecraft", ">=1.17-beta.1");
			if (farPlanePresent.getAsBoolean()) {
				Mixins.addConfiguration("optifabric.compat.architectury-AB.newer-mixins.json");
			} else {
				Mixins.addConfiguration("optifabric.compat.architectury-AB.new-mixins.json");
			}
		} else if (isPresent("architectury", ">=1.0.20")) {
			Mixins.addConfiguration("optifabric.compat.architectury-B.mixins.json");
		} else if (isPresent("architectury", ">=1.0.4")) {
			Mixins.addConfiguration("optifabric.compat.architectury-A.mixins.json");
		}

		if (isPresent("frex", ">=4.3")) {
			Mixins.addConfiguration("optifabric.compat.frex.mixins.json");
		} else if (isPresent("frex", "=4.2")) {
			Mixins.addConfiguration("optifabric.compat.frex-old.mixins.json");
		}

		if (isPresent("full_slabs", ">=1.0.2")) {
			Mixins.addConfiguration("optifabric.compat.full-slabs.mixins.json");
		}

		if (isPresent("amecsapi", "<1.1.2")) {
			Mixins.addConfiguration("optifabric.compat.amecsapi.mixins.json");

			ClassWriter writer = new ClassWriter(0);
			writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, "null", null, "java/lang/Object", null);
			writer.visitEnd(); //Just something to extend Object, only Mixin should see it
			ClassTinkerers.define("null", writer.toByteArray());
		}

		if (isPresent("pswg")) {
			Mixins.addConfiguration("optifabric.compat.pswg.mixins.json");

			if (isPresent("pswg", ">=1.16.4-0.0.15")) {
				injector.predictFuture(RemappingUtils.getClassName("class_276")).ifPresent(node -> {//FrameBuffer
					for (FieldNode field : node.fields) {
						if ("stencilEnabled".equals(field.name) && "Z".equals(field.desc)) {
							Mixins.addConfiguration("optifabric.compat.pswg.extra-mixins.json");
							break;
						}
					}
				});
			}
		}

		if (isPresent("custom-fog", ">=1.2")) {
			Mixins.addConfiguration("optifabric.compat.custom-fog.mixins.json");
		}

		if (isPresent("smooth-chunks")) {
			Mixins.addConfiguration("optifabric.compat.smooth-chunks.mixins.json");
		}

		if (isPresent("enhancedcelestials") && isPresent("minecraft", "<1.19")) {
			if (isPresent("enhancedcelestials", ">=2.0.0")) {
				Mixins.addConfiguration("optifabric.compat.enhancedcelestials.new-mixins.json");
			} else {
				Mixins.addConfiguration("optifabric.compat.enhancedcelestials.mixins.json");
			}
		}

		if (isPresent("cullparticles") && particlesPresent.getAsBoolean()) {
			Mixins.addConfiguration("optifabric.compat.cullparticles.mixins.json");
		}

		if (isPresent("the_aether", "<1.17.1-1.5.0")) {
			Mixins.addConfiguration("optifabric.compat.aether.mixins.json");
		}

		if (isPresent("stacc")) {
			injector.predictFuture(RemappingUtils.getClassName("class_2540")).ifPresent(node -> {//PacketByteBuf
				String desc = RemappingUtils.mapMethodDescriptor("(Lnet/minecraft/class_1799;Z)Lnet/minecraft/class_2540;"); //(ItemStack)PacketByteBuf

				for (MethodNode method : node.methods) {
					if ("writeItemStack".equals(method.name) && desc.equals(method.desc)) {
						if (isPresent("stacc", ">=1.2")) {
							Mixins.addConfiguration("optifabric.compat.stacc.mixins.json");
						} else {
							Mixins.addConfiguration("optifabric.compat.stacc.old-mixins.json");
						}
						break;
					}
				}
			});
		}

		if (isPresent("bannerpp")) {
			Mixins.addConfiguration("optifabric.compat.bannerpp.mixins.json");
		}

		if (isPresent("replaymod")) {
			if (isPresent("minecraft", ">=1.18.1")) {
				Mixins.addConfiguration("optifabric.compat.replaymod.newer-mixins.json");
			} else if (isPresent("minecraft", ">=1.17")) {
				Mixins.addConfiguration("optifabric.compat.replaymod.new-mixins.json");
			} else {
				Mixins.addConfiguration("optifabric.compat.replaymod.mixins.json");
			}
		}

		if (isPresent("zoomify")) {
			Mixins.addConfiguration("optifabric.compat.zoomify.mixins.json");
		}

		if (isPresent("borderlessmining", ">=1.1.6")) {
			Mixins.addConfiguration("optifabric.compat.borderlessmining.new-mixins.json");
		} else if (isPresent("borderlessmining", ">=1.1.3")) {
			Mixins.addConfiguration("optifabric.compat.borderlessmining.mixins.json");
		}

		if (isPresent("fabricloader", ">=0.12.3")) {
			for (Config config : Mixins.getConfigs()) {
				if (config.getName().startsWith("optifabric.")) {
					IMixinConfig settings = config.getConfig();

					if (!settings.hasDecoration(FabricUtil.KEY_MOD_ID)) {
						settings.decorate(FabricUtil.KEY_MOD_ID, "optifabric");
						settings.decorate(FabricUtil.KEY_COMPATIBILITY, FabricUtil.COMPATIBILITY_0_9_2);
					}
				}
			}
		}
	}

	private static boolean isPresent(String modID) {
		return FabricLoader.getInstance().isModLoaded(modID);
	}

	static boolean isPresent(String modID, String versionRange) {
		return isPresent(modID, modMetadata -> compareVersions(versionRange, modMetadata));
	}

	private static boolean isPresent(String modID, Predicate<ModMetadata> extraChecks) {
		if (!isPresent(modID)) return false;

		Optional<ModContainer> modContainer = FabricLoader.getInstance().getModContainer(modID);
		ModMetadata modMetadata = modContainer.map(ModContainer::getMetadata).orElseThrow(() ->
			new RuntimeException("Failed to get mod container for " + modID + ", something has broke badly.")
		);

		return extraChecks.test(modMetadata);
	}

	private static boolean compareVersions(String versionRange, ModMetadata mod) {
		try {
			Predicate<SemanticVersionImpl> predicate = SemanticVersionPredicateParser.create(versionRange);
			SemanticVersionImpl version = new SemanticVersionImpl(mod.getVersion().getFriendlyString(), false);
			return predicate.test(version);
		} catch (@SuppressWarnings("deprecation") net.fabricmc.loader.util.version.VersionParsingException e) {
			System.err.println("Error comparing the version for ".concat(MoreObjects.firstNonNull(mod.getName(), mod.getId())));
			e.printStackTrace();
			return false; //Let's just gamble on the version not being valid also not being a problem
		}
	}
}
