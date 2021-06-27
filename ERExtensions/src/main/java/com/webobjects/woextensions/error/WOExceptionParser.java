/*
 * WOExceptionParser.java
 * (c) Copyright 2001 Apple Computer, Inc. All rights reserved.
 * This a modified version.
 * Original license: http://www.opensource.apple.com/apsl/
 */

package com.webobjects.woextensions.error;

/**
 * WOExceptionParser parse the stack trace of a Java exception (in fact the parse is really
 * made in WOParsedErrorLine).
 * 
 * The stack trace is set in an NSArray that will be used in the UI in the exception page.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSPropertyListSerialization;

public class WOExceptionParser {
	protected NSMutableArray _stackTrace;
	protected Throwable _exception;
	protected String _message;
	protected String _typeException;

	public WOExceptionParser(Throwable exception) {
		super();
		_stackTrace = new NSMutableArray();
		_exception = NSForwardException._originalThrowable(exception);
		_message = _exception.getMessage();
		_typeException = _exception.getClass().getName();
		_parseException();
	}

	protected NSArray _ignoredPackages() {
		NSBundle bundle;
		String path, content;
		NSDictionary dic = null;
		NSMutableArray<NSBundle> allBundles = new NSMutableArray<>(NSBundle.frameworkBundles());
		NSMutableArray<String> ignored = new NSMutableArray<>();

		for (Enumeration enumerator = allBundles.objectEnumerator(); enumerator.hasMoreElements();) {
			bundle = (NSBundle) enumerator.nextElement();
			path = WOApplication.application().resourceManager().pathForResourceNamed("WOIgnoredPackage.plist", bundle.name(), null);
			if (path != null) {
				content = _stringFromFileSafely(path);
				if (content != null) {
					dic = (NSDictionary) NSPropertyListSerialization.propertyListFromString(content);
					if (dic != null && dic.containsKey("ignoredPackages")) {
						@SuppressWarnings("unchecked")
						NSArray<String> tmpArray = (NSArray<String>) dic.objectForKey("ignoredPackages");
						if (tmpArray != null && tmpArray.count() > 0) {
							ignored.addObjectsFromArray(tmpArray);
						}
					}
				}
			}
		}
		System.out.println("_ignoredPackages:: " + ignored);
		return ignored;
	}

	protected void _verifyPackageForLine(WOParsedErrorLine line, NSArray packages) {
		Enumeration enumerator;
		String ignoredPackageName, linePackageName;
		linePackageName = line.packageName();
		enumerator = packages.objectEnumerator();
		while (enumerator.hasMoreElements()) {
			ignoredPackageName = (String) enumerator.nextElement();
			if (linePackageName.startsWith(ignoredPackageName)) {
				line.setIgnorePackage(true);
				break;
			}
		}
	}

	protected void _parseException() {
		StringWriter sWriter = new StringWriter();
		PrintWriter pWriter = new PrintWriter(sWriter, false);
		String string;
		NSArray lines;
		NSArray ignoredPackage;
		WOParsedErrorLine aLine;
		String line;

		int i, size;
		try {
			_exception.printStackTrace(pWriter);
			pWriter.close();
			sWriter.close(); // Added the try/catch as this throws in JDK 1.2
								// aB.
			string = sWriter.toString();
			i = _exception.toString().length(); // We skip the name of the
												// exception and the message for
												// our parse
			if (string.length() > i + 2) { // certain errors don't contain a
											// stack trace
				string = string.substring(i + 2); // Skip the exception type and
													// message
				lines = NSArray.componentsSeparatedByString(string, "\n");
				ignoredPackage = _ignoredPackages();
				size = lines.count();
				_stackTrace = new NSMutableArray(size);
				for (i = 0; i < size; i++) {
					line = ((String) lines.objectAtIndex(i)).trim();
					if (line.startsWith("at ")) {
						// If we don't have an open parenthesis it means that we
						// have probably reach the latest stack trace.
						aLine = new WOParsedErrorLine(line);
						_verifyPackageForLine(aLine, ignoredPackage);
						_stackTrace.addObject(aLine);
					}
				}
			}
		}
		catch (Throwable e) {
			NSLog.err.appendln("WOExceptionParser - exception collecting backtrace data " + e + " - Empty backtrace.");
			NSLog.err.appendln(e);
		}
		if (_stackTrace == null) {
			_stackTrace = new NSMutableArray();
		}
	}

	public NSArray stackTrace() {
		return _stackTrace;
	}

	public String typeException() {
		return _typeException;
	}

	public String message() {
		return _message;
	}

	/**
	 * Return a string from the contents of a file, returning null instead of
	 * any possible exception.
	 * 
	 * TODO I wonder if this has been done somewhere else in the frameworks....
	 */
	private static String _stringFromFileSafely(String path) {
		File f = new File(path);

		if (!f.exists()) {
			return null;
		}

		FileInputStream fis = null;
		byte[] data = null;

		int bytesRead = 0;

		try {
			int size = (int) f.length();
			fis = new FileInputStream(f);
			data = new byte[size];

			while (bytesRead < size) {
				bytesRead += fis.read(data, bytesRead, size - bytesRead);
			}

		}
		catch (java.io.IOException e) {
			return null;
		}
		finally {
			if (fis != null) {
				try {
					fis.close();
				}
				catch (java.io.IOException e) {
					if (NSLog.debugLoggingAllowedForLevelAndGroups(NSLog.DebugLevelInformational, NSLog.DebugGroupIO)) {
						NSLog.debug.appendln("Exception while closing file input stream: " + e.getMessage());
						NSLog.debug.appendln(e);
					}
				}
			}
		}

		if (bytesRead == 0)
			return null;
		return new String(data);
	}
	
	/**
	 * WOParsedErrorLine is the class that will parse an exception line. After
	 * parsing a line (see format in the constructor comment), each instance
	 * will be able to get information about the line, class, method where
	 * the error occurs.
	 *
	 * Evolution : should rewrite the parsing stuff... And verify the real format
	 * of java exception... Be careful, apparently it could happen that the latest
	 * ")" on a line is not present. This is why in the parsing stuff I try to get
	 * the index of this closing parenthesis.
	 */

	public static class WOParsedErrorLine {
	    protected String _packageName;
	    protected String _className;
	    protected String _methodName;
	    protected String _fileName;
	    protected int _line;
	    protected boolean _ignorePackage; // if true, then it will not be possible to display an hyperlink

	    public WOParsedErrorLine(String line) {
	        // line should have the format of an exception, which is normally (below the index value)
	        //        at my.package.name.MyClass.myMethod(FileName.java:lineNumber)
	        //           ^                       ^        ^             ^
	        //        atIndex                    I     classIndex     lineIndex
	        //                                 methodIndex
	        int atIndex, methodIndex, classIndex, lineIndex, index;
	        String string;
	        atIndex = line.indexOf("at ") + 3;
	        classIndex = line.indexOf('(') + 1;
	        methodIndex = line.lastIndexOf('.', classIndex - 2) + 1;
	        lineIndex = line.lastIndexOf(':');
	        if (lineIndex < 0) { // We could potentially do not have the info if we use a JIT
	            _line = -1;
	            _fileName = null;
	        } else {
	            lineIndex++;
	            // Parse the line number
	            index = line.indexOf(')', lineIndex);
	            if (index < 0) {
	                index = line.length();
	            }

	            string = line.substring(lineIndex, index);              // Remove the last ")"

	            try {
	                _line = Integer.parseInt(string);                   // Parse the fileName
	                _fileName = line.substring( classIndex, lineIndex - 1);
	            } catch (NumberFormatException ex) {
	                _line = -1;
	                _fileName = null;
	            }
	        }
	        _methodName = line.substring( methodIndex, classIndex - 1);
	        _packageName = line.substring( atIndex, methodIndex - 1);
	        index = _packageName.lastIndexOf('.');
	        if (index >= 0) {
	            _className = _packageName.substring( index + 1);
	            _packageName = _packageName.substring(0, index);
	        } else _className = _packageName;
	        if (_line < 0) {
	            // JIT Activated so we don't have the class name... we can guess it by using the package info\
	            _fileName = _className + ".java";
	        }
	        _ignorePackage = false; // By default we handle all packages
	    }

	    public String packageName() {
	        return _packageName;
	    }

	    public String className() {
	        return _className;
	    }

	    public String packageClassPath() {
	        if (_packageName.equals(_className)) {
	            return _className;
	        }
	        return _packageName + "." + _className;
	    }

	    public String methodName() {
	        return _methodName;
	    }

	    public boolean isDisable() {
	        return (_line < 0 || _ignorePackage);
	    }

	    protected void setIgnorePackage(boolean yn) { _ignorePackage = yn; }
	    
	    public String fileName() {
	        return _fileName;
	/*        if (_line >= 0)
	            return _fileName;
	        int index = _packageName.lastIndexOf(".");
	        if (index >= 0)
	            return _packageName.substring(index + 1) + ".java";
	        return _packageName + ".java";*/
	    }

	    public String lineNumber() {
	        if (_line >= 0)
	            return String.valueOf(_line);
	        return "NA";
	    }

	    public int line() {
	        return _line;
	    }
	    
	    @Override
	    public String toString() {
	        String lineInfo = (_line >= 0) ? String.valueOf( _line) : "No line info due to compiled code";
	        String fileInfo = (_line >= 0) ? _fileName : "Compiled code no file info";
	        if (_packageName.equals(_className)) {
	            return "class : " + _className + ": " + _methodName + " in file :" + fileInfo + " - line :" + lineInfo;
	        }
	        return "In package : " + _packageName + ", class : " + _className + " method : " + _methodName + " in file :" + fileInfo + " - line :" + lineInfo;
	    }
	}
}