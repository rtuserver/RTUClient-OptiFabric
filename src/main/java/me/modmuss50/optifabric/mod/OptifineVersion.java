package me.modmuss50.optifabric.mod;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileAlreadyExistsException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipError;
import java.util.zip.ZipException;

import com.google.gson.stream.JsonReader;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import net.fabricmc.loader.api.FabricLoader;

import me.modmuss50.optifabric.util.ASMUtils;
import me.modmuss50.optifabric.util.ZipUtils;

public class OptifineVersion {
	public static String version;
	public static String minecraftVersion;
	public static JarType jarType;

	public static File findOptifineJar() throws IOException {
		@SuppressWarnings("deprecation")
		File modsDir = new File(FabricLoader.getInstance().getGameDirectory(), "rtuserver/1.19.4");
		File[] mods = modsDir.listFiles();

		if (mods != null) {
			File optifineJar = null;

			for (File file : mods) {
				if (!file.isDirectory() && "jar".equals(FilenameUtils.getExtension(file.getName())) && !file.getName().startsWith(".") && !file.isHidden()) {
					JarType type = getJarType(file);
					if (type.isError()) {
						jarType = type;
						throw new RuntimeException("An error occurred when trying to find the optifine jar: " + type.name());
					}

					if (type == JarType.OPTIFINE_MOD || type == JarType.OPTIFINE_INSTALLER) {
						if (optifineJar != null) {
							jarType = JarType.DUPLICATED;
							OptifabricError.setError("Please ensure you only have 1 copy of OptiFine in the mods folder!\nFound: %s\n       %s", optifineJar, file);
							throw new FileAlreadyExistsException("Multiple optifine jars: " + file.getName() + " and " + optifineJar.getName());
						}

						jarType = type;
						optifineJar = file;
					}
				}
			}

			if (optifineJar != null) {
				return optifineJar;
			}
		}

		jarType = JarType.MISSING;
		OptifabricError.setError("OptiFabric could not find the Optifine jar in the mods folder:\n%s", modsDir);
		throw new FileNotFoundException("Could not find optifine jar");
	}

	private static JarType getJarType(File file) throws IOException {
		ClassNode classNode;
		try (JarFile jarFile = new JarFile(file)) {
			JarEntry jarEntry = jarFile.getJarEntry("net/optifine/Config.class"); //F1 (1.14.2) - G9 location
			if (jarEntry == null) {
				jarEntry = jarFile.getJarEntry("notch/net/optifine/Config.class"); //H1 (1.17.1) location
			}
			if (jarEntry == null) {
				return JarType.SOMETHING_ELSE;
			}
			classNode = ASMUtils.readClass(jarFile, jarEntry);
		} catch (ZipException | ZipError e) {
			OptifabricError.setError("The jar at " + file + " is corrupt");
			return JarType.CORRUPT_ZIP;
		}

		for (FieldNode fieldNode : classNode.fields) {
			if ("VERSION".equals(fieldNode.name)) {
				version = (String) fieldNode.value;
			}
			if ("MC_VERSION".equals(fieldNode.name)) {
				minecraftVersion = (String) fieldNode.value;
			}
		}

		if (version == null || version.isEmpty() || minecraftVersion == null || minecraftVersion.isEmpty()) {
			OptifabricError.setError("Unable to find OptiFine version from OptiFine jar at " + file);
			return JarType.INCOMPATIBLE;
		}

		String currentMcVersion = "unknown";
		try (JsonReader in = new JsonReader(new InputStreamReader(OptifineVersion.class.getResourceAsStream("/version.json")))) {
			in.beginObject();

			while (in.hasNext()) {
				if ("id".equals(in.nextName())) {
					currentMcVersion = in.nextString();
					break;
				} else {
					in.skipValue();
				}
			}
		} catch (IOException | IllegalStateException e) {
			OptifabricError.setError(e, "Failed to find current minecraft version, please report this");
			e.printStackTrace();
			return JarType.INTERNAL_ERROR;
		}

		if (!currentMcVersion.equals(minecraftVersion)) {
			OptifabricError.setError("This version of OptiFine from %s is not compatible with the current minecraft version\n\nOptifine requires %s you are running %s",
										file, minecraftVersion, currentMcVersion);
			return JarType.INCOMPATIBLE;
		}

		MutableBoolean isInstaller = new MutableBoolean(false);
		ZipUtils.iterateContents(file, (zip, zipEntry) -> {
			if (zipEntry.getName().startsWith("patch/")) {
				isInstaller.setTrue();
				return false;
			} else {
				return true;
			}
		});

		if (isInstaller.isTrue()) {
			return JarType.OPTIFINE_INSTALLER;
		} else {
			return JarType.OPTIFINE_MOD;
		}
	}

	public enum JarType {
		MISSING(true),
		OPTIFINE_MOD(false),
		OPTIFINE_INSTALLER(false),
		INCOMPATIBLE(true),
		CORRUPT_ZIP(true),
		DUPLICATED(true),
		INTERNAL_ERROR(true),
		SOMETHING_ELSE(false);

		private final boolean error;

		JarType(boolean error) {
			this.error = error;
		}

		public boolean isError() {
			return error;
		}
	}
}
