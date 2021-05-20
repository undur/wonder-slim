package com.webobjects.foundation.development;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.w3c.dom.Document;

public class NSMavenProjectBundle extends NSStandardProjectBundle {
	public NSMavenProjectBundle(String projectPath, Properties buildProperties, Document eclipseProject) {
		super(projectPath, buildProperties, eclipseProject);
	}

	public List<String> relativePathForResourceType(NSResourceType type) {
		List<String> aList = new ArrayList<String>();
		if (NSResourceType.Component == type) {
			aList.add("src/main/components");
		}
		else if (NSResourceType.Strings == type) {
			aList.add("src/main/resources");
			aList.add("src/main/woresources");
		}
		else if (NSResourceType.Model == type) {
			aList.add("src/main/resources");
		}
		else if (NSResourceType.D2WModel == type) {
			aList.add("src/main/resources");
			aList.add("src/main/woresources");
		}
		else if (NSResourceType.WebServer == type) {
			aList.add("src/main/webserverresources");
			aList.add("src/main/webserver-resources");
		}
		else if (NSResourceType.Java == type) {
			aList.add("src/main/java");
		}
		else if (NSResourceType.JavaClient == type) {
			aList.add("src/client/java");
		}
		else if (NSResourceType.JavaClientResources == type) {
			aList.add("src/client/resources");
		}
		else if (NSResourceType.InfoPlist == type) {
			aList.add("src/main/woresources");
			aList.add("src/main/resources");
			aList.add("woproject");
		}
		else {
			aList.add("src/main/woresources");
			aList.add("src/main/resources");
			aList.add("src/main/components");
			aList.add("src/main/webserverresources");
			aList.add("src/main/webserver-resources");
		}
		return aList;
	}
}