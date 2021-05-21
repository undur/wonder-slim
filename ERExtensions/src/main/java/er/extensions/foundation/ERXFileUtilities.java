package er.extensions.foundation;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOResourceManager;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSPropertyListSerialization;

public class ERXFileUtilities {

	private static final Logger log = LoggerFactory.getLogger(ERXFileUtilities.class);

	private static Charset _charset = Charset.forName("utf-8");

	private static Charset charset() {
		return _charset;
	}

	/**
	 * Returns the byte array for a given stream.
	 * 
	 * @param in
	 *            stream to get the bytes from
	 * @throws IOException
	 *             if things go wrong
	 * @return byte array of the stream.
	 */
	public static byte[] bytesFromInputStream(InputStream in) throws IOException {
		if (in == null)
			throw new IllegalArgumentException("null input stream");

		try( ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
			int read;
			byte[] buf = new byte[1024 * 50];
			while ((read = in.read(buf)) != -1) {
				bout.write(buf, 0, read);
			}

			return bout.toByteArray();
		}
	}

	/**
	 * Returns a string from the input stream using the specified encoding.
	 * 
	 * @param in
	 *            stream to read
	 * @param encoding
	 *            to be used, <code>null</code> will use the default
	 * @return string representation of the stream
	 * @throws IOException
	 *             if things go wrong
	 */
	private static String stringFromInputStream(InputStream in, String encoding) throws IOException {
		return new String(bytesFromInputStream(in), encoding);
	}

	private static void writeInputStreamToGZippedFile(InputStream stream, File file) throws IOException {
		if (file == null)
			throw new IllegalArgumentException("Attempting to write to a null file!");
		try( GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(file))) {
			ERXFileUtilities.writeInputStreamToOutputStream(stream, false, out, true);
		}
	}

	/**
	 * Copies the contents of the input stream to the given output stream.
	 * 
	 * @param in
	 *            the input stream to copy from
	 * @param closeInputStream
	 *            if true, the input stream will be closed
	 * @param out
	 *            the output stream to copy to
	 * @param closeOutputStream
	 *            if true, the output stream will be closed
	 * @throws IOException
	 *             if there is any failure
	 */
	public static void writeInputStreamToOutputStream(InputStream in, boolean closeInputStream, OutputStream out, boolean closeOutputStream) throws IOException {
		try {
			BufferedInputStream bis = new BufferedInputStream(in);
			try {
				byte buf[] = new byte[1024 * 50]; // 64 KBytes buffer
				int read = -1;
				while ((read = bis.read(buf)) != -1) {
					out.write(buf, 0, read);
				}
			}
			finally {
				if (closeInputStream) {
					bis.close();
				}
			}
			out.flush();
		}
		finally {
			if (closeOutputStream) {
				out.close();
			}
		}
	}

	/**
	 * Writes the contents of <code>s</code> to <code>f</code> using the
	 * platform's default encoding.
	 * 
	 * @param s
	 *            the string to be written to file
	 * @param f
	 *            the destination file
	 * @throws IOException
	 *             if things go wrong
	 */
	public static void stringToFile(String s, File f) throws IOException {
		stringToFile(s, f, System.getProperty("file.encoding"));
	}

	/**
	 * Writes the contents of <code>s</code> to <code>f</code> using specified
	 * encoding.
	 * 
	 * @param s
	 *            the string to be written to file
	 * @param f
	 *            the destination file
	 * @param encoding
	 *            the desired encoding
	 * @throws IOException
	 *             if things go wrong
	 */
	public static void stringToFile(String s, File f, String encoding) throws IOException {
		if (s == null)
			throw new IllegalArgumentException("string argument cannot be null");
		if (f == null)
			throw new IllegalArgumentException("file argument cannot be null");
		if (encoding == null)
			throw new IllegalArgumentException("encoding argument cannot be null");
		try( Reader reader = new BufferedReader(new StringReader(s)) ;
				FileOutputStream fos = new FileOutputStream(f) ;
				Writer out = new BufferedWriter(new OutputStreamWriter(fos, encoding))) {
			int read;
			char buf[] = new char[1024 * 50];
			while ((read = reader.read(buf)) != -1) {
				out.write(buf, 0, read);
			}
		}
	}

	/**
	 * Determines the path of the specified Resource. This is done to get a
	 * single entry point due to the deprecation of pathForResourceNamed
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
	public static String pathForResourceNamed(String fileName, String frameworkName, NSArray<String> languages) {
		String path = null;
		NSBundle bundle = "app".equals(frameworkName) ? NSBundle.mainBundle() : NSBundle.bundleForName(frameworkName);
		if (bundle != null && bundle.isJar()) {
			log.warn("Can't get path when run as jar: {} - {}", frameworkName, fileName);
		}
		else {
			WOApplication application = WOApplication.application();
			if (application != null) {
				URL url = application.resourceManager().pathURLForResourceNamed(fileName, frameworkName, languages);
				if (url != null) {
					path = url.getFile();
				}
			}
			else if (bundle != null) {
				URL url = bundle.pathURLForResourcePath(fileName);
				if (url != null) {
					path = url.getFile();
				}
			}
		}
		return path;
	}

	/**
	 * Get the input stream from the specified Resource.
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
	private static InputStream inputStreamForResourceNamed(String fileName, String frameworkName, NSArray<String> languages) {
		return WOApplication.application().resourceManager().inputStreamForResourceNamed(fileName, frameworkName, languages);
	}

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
	 */
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
	public static Object readPropertyListFromFileInFramework(String fileName, String aFrameWorkName, NSArray<String> languageList, String encoding) {
		Object result = null;
		try( InputStream stream = inputStreamForResourceNamed(fileName, aFrameWorkName, languageList)) {
			if (stream != null) {
				String stringFromFile = stringFromInputStream(stream, encoding);
				result = NSPropertyListSerialization.propertyListFromString(stringFromFile);
			}
		}
		catch (IOException ioe) {
			log.error("ConfigurationManager: Error reading file <{}> from framework {}", fileName, aFrameWorkName);
		}
		return result;
	}

	/**
	 * Copies the source file to the destination. Automatically creates parent
	 * directory or directories of {@code dstFile} if they are missing.
	 *
	 * @param srcFile
	 *            source file
	 * @param dstFile
	 *            destination file which may or may not exist already. If it
	 *            exists, its contents will be overwritten.
	 * @param deleteOriginals
	 *            if {@code true} then {@code srcFile} will be deleted. Note
	 *            that if the appuser has no write rights on {@code srcFile} it
	 *            is NOT deleted unless {@code forceDelete} is true
	 * @param forceDelete
	 *            if {@code true} then missing write rights are ignored and the
	 *            file is deleted.
	 * @throws IOException
	 *             if things go wrong
	 */
	private static void copyFileToFile(File srcFile, File dstFile, boolean deleteOriginals, boolean forceDelete) throws IOException {
		if (srcFile.exists() && srcFile.isFile()) {
			boolean copied = false;
			if (deleteOriginals && (!forceDelete || srcFile.canWrite())) {
				copied = srcFile.renameTo(dstFile);
			}
			if (!copied) {
				File parent = dstFile.getParentFile();
				if (!parent.exists() && !parent.mkdirs()) {
					throw new IOException("Failed to create the directory " + parent + ".");
				}

				try( FileInputStream in = new FileInputStream(srcFile) ;
						FileChannel srcChannel = in.getChannel() ;
						FileOutputStream out = new FileOutputStream(dstFile) ;
						FileChannel dstChannel = out.getChannel()) {
					// Copy file contents from source to destination
					dstChannel.transferFrom(srcChannel, 0, srcChannel.size());
				}
				finally {
					if (deleteOriginals && (srcFile.canWrite() || forceDelete)) {
						if (!srcFile.delete()) {
							throw new IOException("Failed to delete " + srcFile + ".");
						}
					}
				}
			}
		}
	}

	/**
	 * Moves a file from one location to another one. This works different than
	 * java.io.File.renameTo as renameTo does not work across partitions
	 * 
	 * @param source
	 *            the file to move
	 * @param destination
	 *            the destination to move the source to
	 * @throws IOException
	 *             if things go wrong
	 */
	public static void renameTo(File source, File destination) throws IOException {
		if (!source.renameTo(destination)) {
			ERXFileUtilities.copyFileToFile(source, destination, true, true);
		}
	}

	/**
	 * Returns the file name portion of a browser submitted path.
	 * 
	 * @param path
	 *            the full path from the browser
	 * @return the file name portion
	 */
	public static String fileNameFromBrowserSubmittedPath(String path) {
		String fileName = path;
		if (path != null) {
			// Windows
			int separatorIndex = path.lastIndexOf("\\");
			// Unix
			if (separatorIndex == -1) {
				separatorIndex = path.lastIndexOf("/");
			}
			// MacOS 9
			if (separatorIndex == -1) {
				separatorIndex = path.lastIndexOf(":");
			}
			if (separatorIndex != -1) {
				fileName = path.substring(separatorIndex + 1);
			}
			// ... A tiny security check here ... Just in case.
			fileName = fileName.replaceAll("\\.\\.", "_");
		}
		return fileName;
	}

	/**
	 * Reserves a unique file on the filesystem based on the given file name. If
	 * the given file cannot be reserved, then "-1", "-2", etc will be appended
	 * to the filename in front of the extension until a unique file name is
	 * found. This will also ensure that the parent folder is created.
	 * 
	 * @param desiredFile
	 *            the desired destination file to write
	 * @param overwrite
	 *            if true, this will immediately return desiredFile
	 * @return a unique, reserved, filename
	 * @throws IOException
	 *             if the file cannot be created
	 */
	public static File reserveUniqueFile(File desiredFile, boolean overwrite) throws IOException {
		File destinationFile = desiredFile;

		// ... make sure the destination folder exists. This code runs twice
		// here
		// in case there was a race condition.
		File destinationFolder = destinationFile.getParentFile();
		if (!destinationFolder.exists()) {
			if (!destinationFolder.mkdirs()) {
				if (!destinationFolder.exists()) {
					throw new IOException("Unable to create the destination folder '" + destinationFolder + "'.");
				}
			}
		}

		if (!overwrite) {
			// try to reserve file name
			if (!desiredFile.createNewFile()) {
				File parentFolder = desiredFile.getParentFile();
				String fileName = desiredFile.getName();
				// didn't work, so try new name consisting of
				// prefix + number + suffix
				int dotIndex = fileName.lastIndexOf('.');
				String prefix, suffix;

				if (dotIndex < 0) {
					prefix = fileName;
					suffix = "";
				}
				else {
					prefix = fileName.substring(0, dotIndex);
					suffix = fileName.substring(dotIndex);
				}

				int counter = 1;
				// try until we can reserve a file
				do {
					destinationFile = new File(parentFolder, prefix + "-" + counter + suffix);
					counter++;
				}
				while (!destinationFile.createNewFile());
			}
		}

		return destinationFile;
	}
}