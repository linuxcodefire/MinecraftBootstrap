package net.minecraft.bootstrap;

import java.io.File;

public class Util {
	public static final String APPLICATION_NAME = "minecraft";
	
	public static OS getPlatform() {
		final String osName = System.getProperty("os.name").toLowerCase();
		if (osName.contains("win")) {
			return OS.WINDOWS;
		}
		if (osName.contains("mac")) {
			return OS.MACOS;
		}
		if (osName.contains("linux")) {
			return OS.LINUX;
		}
		if (osName.contains("unix")) {
			return OS.LINUX;
		}
		return OS.UNKNOWN;
	}
	
	public static File getWorkingDirectory() {
		final String userHome = System.getProperty("user.home", ".");
		File workingDirectory = null;
		switch (Util.getPlatform()) {
			case LINUX:
			case SOLARIS: {
				workingDirectory = new File(userHome, ".minecraft/");
				break;
			}
			case WINDOWS: {
				final String applicationData = System.getenv("APPDATA");
				final String folder = (applicationData != null) ? applicationData : userHome;
				workingDirectory = new File(folder, ".minecraft/");
				break;
			}
			case MACOS: {
				workingDirectory = new File(userHome, "Library/Application Support/minecraft");
				break;
			}
			default: {
				workingDirectory = new File(userHome, "minecraft/");
				break;
			}
		}
		return workingDirectory;
	}
	
	public enum OS {
		WINDOWS, MACOS, SOLARIS, LINUX, UNKNOWN;
	}
}
