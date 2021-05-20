package com.webobjects.foundation.development;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.w3c.dom.Document;

public class NSFluffyBunnyProjectBundle extends NSStandardProjectBundle {
	public NSFluffyBunnyProjectBundle(String projectPath, Properties buildProperties, Document eclipseProject) {
		super(projectPath, buildProperties, eclipseProject);
	}

	public List<String> relativePathForResourceType(NSResourceType type) {
		List<String> aList = new ArrayList<String>();
		if (NSResourceType.Component == type) {
			aList.add("Components");
		}
		else if (NSResourceType.Strings == type) {
			aList.add("Resources");
		}
		else if (NSResourceType.Model == type) {
			aList.add("Resources");
		}
		else if (NSResourceType.D2WModel == type) {
			aList.add("Resources");
		}
		else if (NSResourceType.WebServer == type) {
			aList.add("WebServerResources");
		}
		else if (NSResourceType.Java == type) {
			aList.add("Resources/Java");
		}
		else if (NSResourceType.JavaClient == type) {
			aList.add("WebServerResources/Java");
		}
		else if (NSResourceType.JavaClientResources == type) {
			aList.add("WebServerResources");
		}
		else if (NSResourceType.InfoPlist == type) {
			aList.add("woproject");
			aList.add(".");
			aList.add("Resources");
		}
		else {
			aList.add("Resources");
			aList.add("Components");
			aList.add("WebServerResources");
		}
		return aList;
	}
}