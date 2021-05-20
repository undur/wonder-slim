package com.webobjects._ideservices;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSProperties;
import com.webobjects.foundation.NSPropertyListSerialization;

public class _PBXProjectWatcher {
	private static int _PBPort;

	private static String _PBHostname;

	private static volatile boolean _printRapidTurnaroundMessage = true;

	private static boolean _communicationDisabled = false;

	static {
		String value = NSProperties.getProperty("ProjectBuilderPort", "8547");
		try {
			_PBPort = Integer.parseInt(value);
		}
		catch (NumberFormatException e) {
			if (NSLog.debugLoggingAllowedForLevel(1))
				NSLog.err.appendln("_PBXProjectWatcher: exception while reading property 'ProjectBuilderPort'. The value '" + value + "' is not an integer. Using port 8547 by default.");
			_PBPort = 8546;
		}
		_PBHostname = NSProperties.getProperty("ProjectBuilderHost", "localhost");
	}

	public static NSArray openProjectsAppropriateForFile(String path) {
		NSArray plist;
		StringBuilder buffer = new StringBuilder(4096);
		buffer.append("<openProjectsAppropriateForFile>");
		buffer.append("<path>" + path + "</path>");
		buffer.append("</openProjectsAppropriateForFile>");
		String result = _sendXMLToPB(buffer.toString());
		if (result.length() > 0) {
			plist = NSPropertyListSerialization.arrayForString(result, true);
		}
		else {
			plist = NSArray.emptyArray();
		}
		return plist;
	}

	public static NSArray targetsInProjectContainingFile(String cookie, String path) {
		NSArray plist;
		StringBuilder buffer = new StringBuilder(4096);
		buffer.append("<targetsInProjectContainingFile>");
		buffer.append("<cookie>" + cookie + "</cookie>");
		buffer.append("<path>" + path + "</path>");
		buffer.append("</targetsInProjectContainingFile>");
		String result = _sendXMLToPB(buffer.toString());
		if (result.length() > 0) {
			plist = NSPropertyListSerialization.arrayForString(result, true);
		}
		else {
			plist = NSArray.emptyArray();
		}
		return plist;
	}

	public static NSArray<String> targetsInProject(String cookie) {
		NSArray<String> plist;
		StringBuilder buffer = new StringBuilder(4096);
		buffer.append("<targetsInProject>");
		buffer.append("<cookie>" + cookie + "</cookie>");
		buffer.append("</targetsInProject>");
		String result = _sendXMLToPB(buffer.toString());
		if (result.length() > 0) {
			plist = NSPropertyListSerialization.arrayForString(result, true);
		}
		else {
			plist = NSArray.emptyArray();
		}
		return plist;
	}

	public static String nameOfProject(String cookie) {
		StringBuilder buffer = new StringBuilder(4096);
		buffer.append("<nameOfProject>");
		buffer.append("<projectCookie>" + cookie + "</projectCookie>");
		buffer.append("</nameOfProject>");
		String result = _sendXMLToPB(buffer.toString());
		return result;
	}

	public static void addFilesToProjectNearFilePreferredInsertionGroupNameAddToTargetsCopyIntoGroupFolderCreateGroupsRecursively(NSArray paths, String cookie, String aFile, String aGroup, NSArray targetCookies, boolean createGroups, boolean recursively) {
		StringBuilder buffer = new StringBuilder(4096);
		buffer.append("<addFilesToProject>");
		buffer.append("<addFiles>" + _xmlStringArray(paths) + "</addFiles>");
		buffer.append("<toProject>" + cookie + "</toProject>");
		buffer.append("<nearFile>" + aFile + "</nearFile>");
		buffer.append("<preferredInsertionGroupName>" + aGroup + "</preferredInsertionGroupName>");
		buffer.append("<addToTargets>" + _xmlStringArray(targetCookies) + "</addToTargets>");
		buffer.append("<copyIntoGroupFolder>" + _xmlBoolean(createGroups) + "</copyIntoGroupFolder>");
		buffer.append("<createGroupsRecursively>" + _xmlBoolean(recursively) + "</createGroupsRecursively>");
		buffer.append("</addFilesToProject>");
		_sendXMLToPB(buffer.toString());
	}

	public static NSArray<String> filesOfTypesInTargetOfProject(NSArray<String> typesArray, String target, String cookie) {
		NSArray<String> plist;
		StringBuilder buffer = new StringBuilder(4096);
		buffer.append("<filesOfTypesInTargetOfProject>");
		buffer.append("<cookie>" + cookie + "</cookie>");
		buffer.append("<target>" + target + "</target>");
		buffer.append("<typesArray>" + _xmlStringArray(typesArray) + "</typesArray>");
		buffer.append("</filesOfTypesInTargetOfProject>");
		String result = _sendXMLToPB(buffer.toString());
		if (result.length() > 0) {
			plist = NSPropertyListSerialization.arrayForString(result, true);
		}
		else {
			plist = NSArray.emptyArray();
		}
		return plist;
	}

	public static String nameOfTargetInProject(String target, String project) {
		StringBuilder buffer = new StringBuilder(4096);
		buffer.append("<nameOfTarget>");
		buffer.append("<targetCookie>" + target + "</targetCookie >");
		buffer.append("<projectCookie>" + project + "</projectCookie >");
		buffer.append("</nameOfTarget>");
		String result = _sendXMLToPB(buffer.toString());
		return result;
	}

	public static void openFile(String filename, int line, String errorMessage) {
		StringBuilder buffer = new StringBuilder(4096);
		buffer.append("<OpenFile><filename>");
		buffer.append(filename);
		buffer.append("</filename><linenumber>");
		buffer.append(line);
		buffer.append("</linenumber><message>");
		buffer.append(errorMessage);
		buffer.append("</message></OpenFile>");
		_sendXMLToPB(buffer.toString());
	}

	public static void addGroup(String name, String path, String projectCookie, String nearFile) {
		StringBuilder buffer = new StringBuilder(4096);
		buffer.append("<addGroup>");
		buffer.append("<name>" + name + "</name >");
		if (path != null)
			buffer.append("<path>" + path + "</path >");
		buffer.append("<projectCookie>" + projectCookie + "</projectCookie >");
		if (nearFile != null)
			buffer.append("<nearFile>" + nearFile + "</nearFile >");
		buffer.append("</addGroup>");
		_sendXMLToPB(buffer.toString());
	}

	public static void addGroupToPreferredInsertionGroup(String name, String path, String projectCookie, String nearFile, String preferredInsertionGroup) {
		StringBuilder buffer = new StringBuilder(4096);
		buffer.append("<addGroupToPreferredInsertionGroup>");
		buffer.append("<name>" + name + "</name >");
		if (path != null)
			buffer.append("<path>" + path + "</path >");
		buffer.append("<projectCookie>" + projectCookie + "</projectCookie >");
		if (nearFile != null)
			buffer.append("<nearFile>" + nearFile + "</nearFile >");
		if (preferredInsertionGroup != null)
			buffer.append("<preferredInsertionGroup>" + preferredInsertionGroup + "</preferredInsertionGroup >");
		buffer.append("</addGroupToPreferredInsertionGroup>");
		_sendXMLToPB(buffer.toString());
	}

	private static String _xmlStringArray(NSArray array) {
		StringBuilder buffer = new StringBuilder(4096);
		buffer.append("<array>");
		for (int i = 0, c = array.count(); i < c; i++) {
			String str = (String) array.objectAtIndex(i);
			buffer.append("<string>" + str + "</string>");
		}
		buffer.append("</array>");
		return buffer.toString();
	}

	private static String _xmlBoolean(boolean value) {
		if (value)
			return "YES";
		return "NO";
	}

	private static String _sendXMLToPB(String command) {
		String result = "";
		if (_communicationDisabled)
			return "";
		try {
			Socket pbSocket = new Socket(_PBHostname, _PBPort);
			OutputStream os = pbSocket.getOutputStream();
			os.write(command.getBytes());
			os.flush();
			try {
				int buffLen = 7000;
				byte[] buffer = new byte[buffLen];
				InputStream inputSt = pbSocket.getInputStream();
				int i = 0, maxI = 50;
				while (inputSt.available() == 0 && i < maxI) {
					Thread.sleep(100L);
					i++;
				}
				if (i == maxI) {
					_communicationDisabled = true;
					NSLog.err.appendln("Error - Couldn't contact Xcode to send XML command " + command);
				}
				while (inputSt.available() > 0) {
					int length = inputSt.read(buffer, 0, (buffLen < inputSt.available()) ? buffLen : inputSt.available());
					result = String.valueOf(result) + new String(buffer, 0, length);
				}
			}
			catch (Exception e) {
				_communicationDisabled = true;
				NSLog.err.appendln(" Error - exception raised when sending xml command to Xcode. XML: " + command + " EXCEPTION: " + e);
				NSLog.err.appendln(e);
				result = "";
			}
			pbSocket.close();
		}
		catch (Exception e) {
			_communicationDisabled = true;
			if (NSLog.debugLoggingAllowedForLevelAndGroups(2, 4L) &&
					System.getProperty("os.name").startsWith("Mac")) {
				if (_printRapidTurnaroundMessage) {
					_printRapidTurnaroundMessage = false;
					NSLog.err.appendln("Cannot use rapid turnaround.  Please start Xcode and open the project for this application.");
				}
				NSLog._conditionallyLogPrivateException(e);
			}
			result = "";
		}
		return result;
	}
}