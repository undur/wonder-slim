package er.extensions;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import com.webobjects.foundation.NSForwardException;

import er.extensions.appserver.ERXApplication;

public class ERXAppRunner {

	private static boolean _appInitialized = false;

	/**
	 * Initializes your WOApplication programmatically (for use in test cases
	 * and main methods) with the assumption that the current directory is your
	 * main bundle URL.
	 * 
	 * @param applicationSubclass your Application subclass
	 * @param args the commandline arguments for your application
	 */
	public static void initApp(Class applicationSubclass, String[] args) {
		try {
			File woaFolder = new File(".").getCanonicalFile();
			if (!woaFolder.getName().endsWith(".woa")) {
				if (new File(woaFolder, ".project").exists()) {
					File buildFolder = new File(new File(woaFolder, "build"), woaFolder.getName() + ".woa");
					if (buildFolder.exists()) {
						woaFolder = buildFolder;
					}
					else {
						File distFolder = new File(new File(woaFolder, "dist"), woaFolder.getName() + ".woa");
						if (distFolder.exists()) {
							woaFolder = distFolder;
						}
						else {
							// Bundle-less builds. Yay!
							// throw new IllegalArgumentException("You must run
							// your application from a .woa folder to call this
							// method.");
						}
					}
				}
			}
			initApp(null, woaFolder.toURI().toURL(), applicationSubclass, args);
		}
		catch (IOException e) {
			throw new NSForwardException(e);
		}
	}

	/**
	 * Initializes your WOApplication programmatically (for use in test cases and main methods).
	 * 
	 * @param mainBundleName the name of your main bundle
	 * @param applicationSubclass your Application subclass
	 * @param args the commandline arguments for your application
	 */
	public static void initApp(String mainBundleName, Class applicationSubclass, String[] args) {
		initApp(mainBundleName, null, applicationSubclass, args);
	}

	/**
	 * Initializes your WOApplication programmatically (for use in test cases and main methods).
	 * 
	 * @param mainBundleName the name of your main bundle (or null to use mainBundleURL)
	 * @param mainBundleURL the URL to your main bundle (ignored if mainBundleName is set)
	 * @param applicationSubclass your Application subclass
	 * @param args the commandline arguments for your application
	 */
	public static void initApp(String mainBundleName, URL mainBundleURL, Class applicationSubclass, String[] args) {
		if (_appInitialized) {
			return;
		}
		try {
			ERXApplication.setup(args);
			if (mainBundleURL != null) {
				System.setProperty("webobjects.user.dir", new File(mainBundleURL.getFile()).getCanonicalPath());
			}
			// Odds are you are only using this method for test cases and
			// development mode
			System.setProperty("er.extensions.ERXApplication.developmentMode", "true");
			ERXApplication.primeApplication(mainBundleName, mainBundleURL, applicationSubclass.getName());
			// NSNotificationCenter.defaultCenter().postNotification(new
			// NSNotification(ERXApplication.ApplicationDidCreateNotification,
			// WOApplication.application()));
		}
		catch (IOException e) {
			throw new NSForwardException(e);
		}
		_appInitialized = true;
	}
}