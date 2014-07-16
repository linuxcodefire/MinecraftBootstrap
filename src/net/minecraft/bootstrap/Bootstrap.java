package net.minecraft.bootstrap;

import java.awt.Font;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;

import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import net.minecraft.hopper.HopperService;
import LZMA.LzmaInputStream;

public class Bootstrap extends JFrame {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final Font MONOSPACED;
	public static final String LAUNCHER_URL = "https://s3.amazonaws.com/Minecraft.Download/launcher/launcher.pack.lzma";
	private final File workDir;
	private final File launcherJar;
	private final File packedLauncherJar;
	private final File packedLauncherJarNew;
	private final JTextArea textArea;
	private final JScrollPane scrollPane;
	private final PasswordAuthentication proxyAuth;
	private final String[] remainderArgs;
	private final StringBuilder outputBuffer;
	private Proxy proxy;
	
	public Bootstrap(final File workDir, final Proxy proxy, final PasswordAuthentication proxyAuth, final String[] remainderArgs) {
		super("Minecraft Launcher");
		outputBuffer = new StringBuilder();
		this.workDir = workDir;
		this.proxy = proxy;
		this.proxyAuth = proxyAuth;
		this.remainderArgs = remainderArgs;
		launcherJar = new File(workDir, "launcher.jar");
		packedLauncherJar = new File(workDir, "launcher.pack.lzma");
		packedLauncherJarNew = new File(workDir, "launcher.pack.lzma.new");
		this.setSize(854, 480);
		setDefaultCloseOperation(3);
		(textArea = new JTextArea()).setLineWrap(true);
		textArea.setEditable(false);
		textArea.setFont(Bootstrap.MONOSPACED);
		((DefaultCaret) textArea.getCaret()).setUpdatePolicy(1);
		(scrollPane = new JScrollPane(textArea)).setBorder(null);
		scrollPane.setVerticalScrollBarPolicy(22);
		this.add(scrollPane);
		setLocationRelativeTo(null);
		setVisible(true);
		println("Bootstrap (v5)");
		println("Current time is " + DateFormat.getDateTimeInstance(2, 2, Locale.US).format(new Date()));
		println("System.getProperty('os.name') == '" + System.getProperty("os.name") + "'");
		println("System.getProperty('os.version') == '" + System.getProperty("os.version") + "'");
		println("System.getProperty('os.arch') == '" + System.getProperty("os.arch") + "'");
		println("System.getProperty('java.version') == '" + System.getProperty("java.version") + "'");
		println("System.getProperty('java.vendor') == '" + System.getProperty("java.vendor") + "'");
		println("System.getProperty('sun.arch.data.model') == '" + System.getProperty("sun.arch.data.model") + "'");
		println("");
	}
	
	public void execute(final boolean force) {
		if (packedLauncherJarNew.isFile()) {
			println("Found cached update");
			renameNew();
		}
		final Downloader.Controller controller = new Downloader.Controller();
		if (force || !packedLauncherJar.exists()) {
			final Downloader downloader = new Downloader(controller, this, proxy, null, packedLauncherJarNew);
			downloader.run();
			if (controller.hasDownloadedLatch.getCount() != 0L) {
				throw new FatalBootstrapError("Unable to download while being forced");
			}
			renameNew();
		} else {
			final String md5 = getMd5(packedLauncherJar);
			final Thread thread = new Thread(new Downloader(controller, this, proxy, md5, packedLauncherJarNew));
			thread.setName("Launcher downloader");
			thread.start();
			try {
				println("Looking for update");
				final boolean wasInTime = controller.foundUpdateLatch.await(3L, TimeUnit.SECONDS);
				if (controller.foundUpdate.get()) {
					println("Found update in time, waiting to download");
					controller.hasDownloadedLatch.await();
					renameNew();
				} else if (!wasInTime) {
					println("Didn't find an update in time.");
				}
			} catch (InterruptedException e) {
				throw new FatalBootstrapError("Got interrupted: " + e.toString());
			}
		}
		unpack();
		startLauncher(launcherJar);
	}
	
	public void unpack() {
		final File lzmaUnpacked = getUnpackedLzmaFile(packedLauncherJar);
		InputStream inputHandle = null;
		OutputStream outputHandle = null;
		println("Reversing LZMA on " + packedLauncherJar + " to " + lzmaUnpacked);
		try {
			inputHandle = new LzmaInputStream(new FileInputStream(packedLauncherJar));
			outputHandle = new FileOutputStream(lzmaUnpacked);
			final byte[] buffer = new byte[65536];
			for (int read = inputHandle.read(buffer); read >= 1; read = inputHandle.read(buffer)) {
				outputHandle.write(buffer, 0, read);
			}
		} catch (Exception e) {
			throw new FatalBootstrapError("Unable to un-lzma: " + e);
		} finally {
			Bootstrap.closeSilently(inputHandle);
			Bootstrap.closeSilently(outputHandle);
		}
		println("Unpacking " + lzmaUnpacked + " to " + launcherJar);
		JarOutputStream jarOutputStream = null;
		try {
			jarOutputStream = new JarOutputStream(new FileOutputStream(launcherJar));
			Pack200.newUnpacker().unpack(lzmaUnpacked, jarOutputStream);
		} catch (Exception e2) {
			throw new FatalBootstrapError("Unable to un-pack200: " + e2);
		} finally {
			Bootstrap.closeSilently(jarOutputStream);
		}
		println("Cleaning up " + lzmaUnpacked);
		lzmaUnpacked.delete();
	}
	
	public static void closeSilently(final Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException ex) {
			}
		}
	}
	
	private File getUnpackedLzmaFile(final File packedLauncherJar) {
		String filePath = packedLauncherJar.getAbsolutePath();
		if (filePath.endsWith(".lzma")) {
			filePath = filePath.substring(0, filePath.length() - 5);
		}
		return new File(filePath);
	}
	
	public String getMd5(final File file) {
		DigestInputStream stream = null;
		try {
			stream = new DigestInputStream(new FileInputStream(file), MessageDigest.getInstance("MD5"));
			final byte[] buffer = new byte[65536];
			for (int read = stream.read(buffer); read >= 1; read = stream.read(buffer)) {
			}
		} catch (Exception ignored) {
			return null;
		} finally {
			Bootstrap.closeSilently(stream);
		}
		return String.format("%1$032x", new BigInteger(1, stream.getMessageDigest().digest()));
	}
	
	public void println(final String string) {
		this.print(string + "\n");
	}
	
	public void print(final String string) {
		System.out.print(string);
		outputBuffer.append(string);
		final Document document = textArea.getDocument();
		final JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
		final boolean shouldScroll = scrollBar.getValue() + scrollBar.getSize().getHeight() + Bootstrap.MONOSPACED.getSize() * 2 > scrollBar.getMaximum();
		try {
			document.insertString(document.getLength(), string, null);
		} catch (BadLocationException ex) {
		}
		if (shouldScroll) {
			SwingUtilities.invokeLater(() -> scrollBar.setValue(Integer.MAX_VALUE));
		}
	}
	
	public void startLauncher(final File launcherJar) {
		println("Starting launcher.");
		try {
			@SuppressWarnings("resource")
			final Class<?> aClass = new URLClassLoader(new URL[] { launcherJar.toURI().toURL() }).loadClass("net.minecraft.launcher.Launcher");
			final Constructor<?> constructor = aClass.getConstructor(JFrame.class, File.class, Proxy.class, PasswordAuthentication.class, String[].class, Integer.class);
			constructor.newInstance(this, workDir, proxy, proxyAuth, remainderArgs, 5);
		} catch (Exception e) {
			throw new FatalBootstrapError("Unable to start: " + e);
		}
	}
	
	public void renameNew() {
		if (packedLauncherJar.exists() && !packedLauncherJar.isFile() && !packedLauncherJar.delete()) {
			throw new FatalBootstrapError("while renaming, target path: " + packedLauncherJar.getAbsolutePath() + " is not a file and we failed to delete it");
		}
		if (packedLauncherJarNew.isFile()) {
			println("Renaming " + packedLauncherJarNew.getAbsolutePath() + " to " + packedLauncherJar.getAbsolutePath());
			if (packedLauncherJarNew.renameTo(packedLauncherJar)) {
				println("Renamed successfully.");
			} else {
				if (packedLauncherJar.exists() && !packedLauncherJar.canWrite()) {
					throw new FatalBootstrapError("unable to rename: target" + packedLauncherJar.getAbsolutePath() + " not writable");
				}
				println("Unable to rename - could be on another filesystem, trying copy & delete.");
				if (packedLauncherJarNew.exists() && packedLauncherJarNew.isFile()) {
					try {
						Bootstrap.copyFile(packedLauncherJarNew, packedLauncherJar);
						if (packedLauncherJarNew.delete()) {
							println("Copy & delete succeeded.");
						} else {
							println("Unable to remove " + packedLauncherJarNew.getAbsolutePath() + " after copy.");
						}
						return;
					} catch (IOException e) {
						throw new FatalBootstrapError("unable to copy:" + e);
					}
				}
				println("Nevermind... file vanished?");
			}
		}
	}
	
	public static void copyFile(final File source, final File target) throws IOException {
		if (!target.exists()) {
			target.createNewFile();
		}
		FileChannel sourceChannel = null;
		FileChannel targetChannel = null;
		try {
			sourceChannel = new FileInputStream(source).getChannel();
			targetChannel = new FileOutputStream(target).getChannel();
			targetChannel.transferFrom(sourceChannel, 0L, sourceChannel.size());
		} finally {
			if (sourceChannel != null) {
				sourceChannel.close();
			}
			if (targetChannel != null) {
				targetChannel.close();
			}
		}
	}
	
	public static void main(final String[] args) throws IOException {
		System.setProperty("java.net.preferIPv4Stack", "true");
		final OptionParser optionParser = new OptionParser();
		optionParser.allowsUnrecognizedOptions();
		optionParser.accepts("help", "Show help").forHelp();
		optionParser.accepts("force", "Force updating");
		final OptionSpec<String> proxyHostOption = optionParser.accepts("proxyHost", "Optional").withRequiredArg();
		final OptionSpec<Integer> proxyPortOption = optionParser.accepts("proxyPort", "Optional").withRequiredArg().defaultsTo("8080", new String[0]).ofType(Integer.class);
		final OptionSpec<String> proxyUserOption = optionParser.accepts("proxyUser", "Optional").withRequiredArg();
		final OptionSpec<String> proxyPassOption = optionParser.accepts("proxyPass", "Optional").withRequiredArg();
		final OptionSpec<File> workingDirectoryOption = optionParser.accepts("workDir", "Optional").withRequiredArg().ofType(File.class).defaultsTo(Util.getWorkingDirectory(), new File[0]);
		final OptionSpec<String> nonOptions = optionParser.nonOptions();
		OptionSet optionSet;
		try {
			optionSet = optionParser.parse(args);
		} catch (OptionException e) {
			optionParser.printHelpOn(System.out);
			System.out.println("(to pass in arguments to minecraft directly use: '--' followed by your arguments");
			return;
		}
		if (optionSet.has("help")) {
			optionParser.printHelpOn(System.out);
			return;
		}
		final String hostName = optionSet.valueOf(proxyHostOption);
		Proxy proxy = Proxy.NO_PROXY;
		if (hostName != null) {
			try {
				proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(hostName, optionSet.valueOf(proxyPortOption)));
			} catch (Exception ex) {
			}
		}
		final String proxyUser = optionSet.valueOf(proxyUserOption);
		final String proxyPass = optionSet.valueOf(proxyPassOption);
		PasswordAuthentication passwordAuthentication = null;
		if (!proxy.equals(Proxy.NO_PROXY) && Bootstrap.stringHasValue(proxyUser) && Bootstrap.stringHasValue(proxyPass)) {
			final PasswordAuthentication auth;
			passwordAuthentication = (auth = new PasswordAuthentication(proxyUser, proxyPass.toCharArray()));
			Authenticator.setDefault(new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return auth;
				}
			});
		}
		final File workingDirectory = optionSet.valueOf(workingDirectoryOption);
		if (workingDirectory.exists() && !workingDirectory.isDirectory()) {
			throw new FatalBootstrapError("Invalid working directory: " + workingDirectory);
		}
		if (!workingDirectory.exists() && !workingDirectory.mkdirs()) {
			throw new FatalBootstrapError("Unable to create directory: " + workingDirectory);
		}
		final List<String> strings = optionSet.valuesOf(nonOptions);
		final String[] remainderArgs = strings.toArray(new String[strings.size()]);
		final boolean force = optionSet.has("force");
		final Bootstrap frame = new Bootstrap(workingDirectory, proxy, passwordAuthentication, remainderArgs);
		try {
			frame.execute(force);
		} catch (Throwable t) {
			final ByteArrayOutputStream stracktrace = new ByteArrayOutputStream();
			t.printStackTrace(new PrintStream(stracktrace));
			final StringBuilder report = new StringBuilder();
			report.append(stracktrace).append("\n\n-- Head --\nStacktrace:\n").append(stracktrace).append("\n\n").append(frame.outputBuffer);
			report.append("\tMinecraft.Bootstrap Version: 5");
			try {
				HopperService.submitReport(proxy, report.toString(), "Minecraft.Bootstrap", "5");
			} catch (Throwable t2) {
			}
			frame.println("FATAL ERROR: " + stracktrace.toString());
			frame.println("\nPlease fix the error and restart.");
		}
	}
	
	public static boolean stringHasValue(final String string) {
		return string != null && !string.isEmpty();
	}
	
	static {
		MONOSPACED = new Font("Monospaced", 0, 12);
	}
}
