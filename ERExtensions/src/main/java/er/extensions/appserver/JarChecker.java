package er.extensions.appserver;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.log4j.Logger;

import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSMutableSet;

/**
 * Utility class to track down duplicate items in the class path. Reports
 * duplicate packages and packages that are present in different versions.
 * 
 * @author ak
 */
public class JarChecker {
	private static final Logger startupLog = Logger.getLogger("er.extensions.ERXApplication.Startup");

	private static class Entry {
		long _size;
		String _jar;

		public Entry(long aL, String jar) {
			_size = aL;
			_jar = jar;
		}

		public long size() {
			return _size;
		}

		public String jar() {
			return _jar;
		}

		@Override
		public boolean equals(Object other) {
			if (other != null && other instanceof Entry) {
				return ((Entry) other).size() == size();
			}
			return false;
		}

		@Override
		public int hashCode() {
			return (int) _size;
		}

		@Override
		public String toString() {
			return size() + "->" + jar();
		}
	}

	private NSMutableDictionary<String, NSMutableArray<String>> packages = new NSMutableDictionary<>();

	private NSMutableDictionary<String, NSMutableSet<Entry>> classes = new NSMutableDictionary<>();

	void processJar(String jar) {
		File jarFile = new File(jar);
		if (!jarFile.exists() || jarFile.isDirectory()) {
			return;
		}
		try( JarFile f = new JarFile(jar)) {
			for (Enumeration<JarEntry> enumerator = f.entries(); enumerator.hasMoreElements();) {
				JarEntry entry = enumerator.nextElement();
				String name = entry.getName();
				if (entry.getName().endsWith("/") && !(name.matches("^\\w+/$") || name.startsWith("META-INF"))) {
					NSMutableArray<String> bundles = packages.objectForKey(name);
					if (bundles == null) {
						bundles = new NSMutableArray<>();
						packages.setObjectForKey(bundles, name);
					}
					bundles.addObject(jar);
				}
				else if (!(name.startsWith("src") || name.startsWith("META-INF"))) {
					Entry e = new Entry(entry.getSize(), jar);
					NSMutableSet<Entry> set = classes.objectForKey(name);
					if (set == null) {
						set = new NSMutableSet<>();
						classes.setObjectForKey(set, name);
					}
					set.addObject(e);
				}
			}
		}
		catch (IOException e) {
			startupLog.error("Error in processing jar: " + jar, e);
		}
	}

	void reportErrors() {
		StringBuilder sb = new StringBuilder();
		String message = null;

		NSMutableArray<String> keys = new NSMutableArray<>( packages.allKeys() );
		Collections.sort(keys);

		for (Enumeration<String> enumerator = keys.objectEnumerator(); enumerator.hasMoreElements();) {
			String packageName = enumerator.nextElement();
			NSMutableArray<String> bundles = packages.objectForKey(packageName);
			if (bundles.count() > 1) {
				sb.append('\t').append(packageName).append("->").append(bundles).append('\n');
			}
		}
		message = sb.toString();
		if (message.length() > 0) {
			startupLog.debug("The following packages appear multiple times:\n" + message);
		}
		sb = new StringBuilder();
		NSMutableSet<String> classPackages = new NSMutableSet<>();
		keys = new NSMutableArray<>( classes.allKeys());
		Collections.sort(keys);
		
		for (Enumeration<String> enumerator = keys.objectEnumerator(); enumerator.hasMoreElements();) {
			String className = enumerator.nextElement();
			String packageName = className.replaceAll("/[^/]+?$", "");
			NSMutableSet<Entry> bundles = classes.objectForKey(className);
			if (bundles.count() > 1 && !classPackages.containsObject(packageName)) {
				sb.append('\t').append(packageName).append("->").append(bundles).append('\n');
				classPackages.addObject(packageName);
			}
		}
		message = sb.toString();
		if (message.length() > 0) {
			startupLog.debug("The following packages have different versions, you should remove the version you don't want:\n" + message);
		}
	}
}