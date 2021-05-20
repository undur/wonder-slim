package com.webobjects._ideservices;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSPathUtilities;
import com.webobjects.foundation.NSProperties;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation._NSUtilities;

public class _WOLaunchServices {
	private static WOApplication woapp = null;

	private static final String openExecutable = "/usr/bin/open";

	public static WOApplication application() {
		if (woapp == null)
			woapp = WOApplication.application();
		return woapp;
	}

	@Deprecated
	public static void _debugString(String aString) {
		if (WOApplication._isDebuggingEnabled())
			NSLog.debug.appendln(aString);
	}

	private boolean _openClientApplication(String urlString, String osName) {
		if (_NSUtilities.classWithName("com.webobjects.eodistribution.WOJavaClientComponent") == null)
			return false;
		NSBundle mainBundle = NSBundle.mainBundle();
		if (mainBundle != null) {
			String path = mainBundle.bundlePathURL().getPath();
			String name = mainBundle.name();
			if (path != null) {
				path = NSPathUtilities.stringByAppendingPathComponent(path, "Contents");
				String lowercaseOsName = osName.toLowerCase();
				if (lowercaseOsName.startsWith("mac")) {
					path = NSPathUtilities.stringByAppendingPathComponent(path, "MacOS");
					path = NSPathUtilities.stringByAppendingPathComponent(path, String.valueOf(name) + "_Client");
				}
				else if (lowercaseOsName.startsWith("win")) {
					path = NSPathUtilities.stringByAppendingPathComponent(path, "Windows");
					path = NSPathUtilities.stringByAppendingPathComponent(path, String.valueOf(name) + "_Client.cmd");
				}
				else {
					return false;
				}
				File scriptFile = new File(path);
				if (scriptFile.exists() && scriptFile.isFile()) {
					try {
						String command = String.valueOf(path) + " " + urlString;
						NSLog.debug.appendln("Opening client application with script:\n" + command);
						Runtime.getRuntime().exec(String.valueOf(command) + " 1>>/dev/console 2>&1");
					}
					catch (IOException exception) {
						throw NSForwardException._runtimeExceptionForThrowable(exception);
					}
					return true;
				}
			}
		}
		if (application().autoOpenInBrowser()) {
			_debugString("Unable to locate client launch script in your application, using the Auto Open In Browser feature instead of the Auto Open Client Application feature.");
		}
		else {
			_debugString("Unable to locate client launch script in your application, auto opening client application launch will not work. You may use the Auto Open In Browser feature instead of the Auto Open Client Application feature when starting the application.");
		}
		return false;
	}

	private void _openURL(String anURLString, String osName) {
		File openURLFile = null;
		openURLFile = new File("/usr/bin/open");
		if (openURLFile.exists() && openURLFile.isFile() && !openURLFile.canWrite()) {
			try {
				Runtime.getRuntime().exec("/usr/bin/open " + anURLString);
			}
			catch (IOException e) {
				throw NSForwardException._runtimeExceptionForThrowable(e);
			}
		}
		else {
			_debugString("Unable to locate /usr/bin/open on your computer, AutoOpen launch will not work");
			return;
		}
	}

	private static void _openWebServicesAssistantUrl(String url) throws Exception {
		File tempFile = File.createTempFile("openWebServicesAssistantURL", null);
		FileWriter tempFileWriter = new FileWriter(tempFile);
		tempFileWriter.write(url);
		tempFileWriter.close();
		Runtime.getRuntime().exec("/usr/bin/open -a WebServicesAssistant.app " + tempFile).waitFor();
	}

	public void _openInitialURL() {
		String anURLString = null;
		String anDebugString = null;
		String anErrorString = null;
		if (!application().wasMainInvoked()) {
			anURLString = application().servletConnectURL();
		}
		else if (application().isDirectConnectEnabled()) {
			anURLString = application().directConnectURL();
		}
		else {
			anURLString = application().webserverConnectURL();
		}
		if (anURLString != null) {
			String osName = System.getProperty("os.name");
			boolean handleAutoOpenInBrowser = application().autoOpenClientApplication() ? (!_openClientApplication(anURLString, osName)) : true;
			boolean isWebServicesAssistantDisabled = false;
			if (NSProperties.getProperty("WSAssistantEnabled") != null)
				isWebServicesAssistantDisabled = !NSPropertyListSerialization.booleanForString(NSProperties.getProperty("WSAssistantEnabled"));
			boolean isWebServicesAssistantAwareApp = (_NSUtilities.classWithName("com.webobjects.webservices.generation._WSDirectAction") != null);
			if (handleAutoOpenInBrowser && !isWebServicesAssistantDisabled && isWebServicesAssistantAwareApp) {
				try {
					_openWebServicesAssistantUrl(anURLString);
					anDebugString = "The URL for webserver connect is:\n" + application().webserverConnectURL();
					if (application().isDirectConnectEnabled())
						anDebugString = String.valueOf(anDebugString) + "\nThe URL for direct connect is:\n" + anURLString;
				}
				catch (Exception e) {
					throw NSForwardException._runtimeExceptionForThrowable(e);
				}
			}
			else if (handleAutoOpenInBrowser && application().autoOpenInBrowser()) {
				if (application()._isSupportedDevelopmentPlatform()) {
					anDebugString = "Opening application's URL in browser:\n" + anURLString;
					_openURL(anURLString, osName);
				}
				else {
					anErrorString = "Your application is not running on a supported development platform. AutoLaunch will not work.\nYour application's URL is:\n" + anURLString;
				}
			}
			else if (application().wasMainInvoked()) {
				anDebugString = "The URL for webserver connect is:\n" + application().webserverConnectURL();
				if (application().isDirectConnectEnabled())
					anDebugString = String.valueOf(anDebugString) + "\nThe URL for direct connect is:\n" + anURLString;
			}
			else {
				anDebugString = "The URL for webserver connect through Servlet Container is:\n" + anURLString;
			}
		}
		else {
			anErrorString = "Unable to compute your application's URL.";
		}
		NSLog.debug.appendln(anDebugString);
		NSLog.err.appendln(anErrorString);
	}
}