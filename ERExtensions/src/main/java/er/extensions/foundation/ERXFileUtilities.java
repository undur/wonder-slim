package er.extensions.foundation;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOResourceManager;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSPropertyListSerialization;

@Deprecated
public class ERXFileUtilities {

	private static final Logger log = LoggerFactory.getLogger(ERXFileUtilities.class);

	/**
	 * Determines the path URL of the specified Resource. This is done to get a
	 * single entry point due to the deprecation of pathForResourceNamed. In a
	 * later version this will call out to the resource managers new methods
	 * directly.
	 * 
	 * @param fileName
	 *            name of the file
	 * @param frameworkName
	 *            name of the framework, <code>null</code> or "app" for the
	 *            application bundle
	 * @param languages
	 *            array of languages to get localized resource or
	 *            <code>null</code>
	 * @return the absolutePath method off of the file object
	 */
	@Deprecated
	public static URL pathURLForResourceNamed(String fileName, String frameworkName, NSArray<String> languages) {
		URL url = null;
		WOApplication application = WOApplication.application();
		if (application != null) {
			WOResourceManager resourceManager = application.resourceManager();
			if (resourceManager != null) {
				url = resourceManager.pathURLForResourceNamed(fileName, frameworkName, languages);
			}
		}
		return url;
	}

	/**
	 * Reads a file in from the file system for the given set of languages and
	 * then parses the file as if it were a property list, using the platform's
	 * default encoding.
	 * 
	 * @param fileName
	 *            name of the file
	 * @param aFrameWorkName
	 *            name of the framework, <code>null</code> or 'app' for the
	 *            application bundle.
	 * @param languageList
	 *            language list search order
	 * @return de-serialized object from the plist formatted file specified.
	 * 
	 * FIXME: Eliminate this encoding guesswork horror
	 */
	@Deprecated
	public static Object readPropertyListFromFileInFramework(String fileName, String aFrameWorkName, NSArray<String> languageList) {
		Object plist = null;
		try {
			plist = readPropertyListFromFileInFramework(fileName, aFrameWorkName, languageList, System.getProperty("file.encoding"));
		}
		catch (IllegalArgumentException e) {
			try {
				// BUGFIX: we didnt use an encoding before, so java tried to
				// guess the encoding. Now some Localizable.strings plists
				// are encoded in MacRoman whereas others are UTF-16.
				plist = readPropertyListFromFileInFramework(fileName, aFrameWorkName, languageList, "utf-16");
			}
			catch (IllegalArgumentException e1) {
				// OK, whatever it is, try to parse it!
				plist = readPropertyListFromFileInFramework(fileName, aFrameWorkName, languageList, "utf-8");
			}
		}
		return plist;
	}

	/**
	 * Reads a file in from the file system for the given set of languages and
	 * then parses the file as if it were a property list, using the specified
	 * encoding.
	 *
	 * @param fileName
	 *            name of the file
	 * @param aFrameWorkName
	 *            name of the framework, <code>null</code> or 'app' for the
	 *            application bundle.
	 * @param languageList
	 *            language list search order
	 * @param encoding
	 *            the encoding used with <code>fileName</code>
	 * @return de-serialized object from the plist formatted file specified.
	 */
	@Deprecated
	public static Object readPropertyListFromFileInFramework(String fileName, String aFrameWorkName, NSArray<String> languageList, String encoding) {
		Object result = null;
		try( InputStream stream = WOApplication.application().resourceManager().inputStreamForResourceNamed(fileName, aFrameWorkName, languageList)) {
			if (stream != null) {
				String stringFromFile = ERXStringUtilities.stringFromInputStream(stream, encoding);
				result = NSPropertyListSerialization.propertyListFromString(stringFromFile);
			}
		}
		catch (IOException ioe) {
			log.error("ConfigurationManager: Error reading file <{}> from framework {}", fileName, aFrameWorkName);
		}
		return result;
	}
}