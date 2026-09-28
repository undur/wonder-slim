package er.extensions.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * What counts as a web server resource, from resource URLs as WebObjects composes them (the forms below were observed in a
 * deployed application and in development)
 */
public class ERXAppBasedResourceManagerTest {

	private static boolean servable( final String url ) {
		return ERXAppBasedResourceManager.isInWebServerResourcesFolder( url );
	}

	@Test
	public void webServerResourcesAreServable() {
		assertTrue( servable( "/WebObjects/App.woa/Contents/WebServerResources/public/robots.txt" ) );
		assertTrue( servable( "/WebObjects/Frameworks/AjaxSlim.framework/WebServerResources/idiomorph.js" ) );
		assertTrue( servable( "/WebObjects/App.woa/src/main/webserver-resources/public/robots.txt" ) );
		assertTrue( servable( "/WebObjects/Frameworks/ERExtensions.framework/src/main/webserver-resources/x.css" ) );
	}

	@Test
	public void privateResourcesAreNot() {
		assertFalse( servable( "/WebObjects/App.woa/Contents/Resources/Properties" ) );
		assertFalse( servable( "/WebObjects/App.woa/Contents/Info.plist" ) );
		assertFalse( servable( "/WebObjects/Frameworks/ERExtensions.framework/Resources/AdditionalMimeTypes.plist" ) );
		assertFalse( servable( "/WebObjects/App.woa/src/main/woresources/Properties" ) );
	}

	@Test
	public void aFolderOrFileNamedLikeOneElsewhereDoesntCount() {
		assertFalse( servable( "/WebObjects/App.woa/Contents/Resources/WebServerResources/secret.txt" ) );
		assertFalse( servable( "/WebObjects/App.woa/Contents/Resources/webserver-resources-notes.txt" ) );
		assertFalse( servable( "/WebObjects/App.woa/src/main/woresources/webserver-resources/secret.txt" ) );
		assertFalse( servable( "/opt/WebServerResources/App.woa/Contents/Resources/Properties" ) );
	}

	@Test
	public void noWayBackOutOfTheFolder() {
		assertFalse( servable( "/WebObjects/App.woa/Contents/WebServerResources/../Resources/Properties" ) );
		assertFalse( servable( "/WebObjects/App.woa/Contents/WebServerResources/./x/../../Resources/Properties" ) );
	}

	@Test
	public void aURLWithoutABundleIsNot() {
		assertFalse( servable( "/WebServerResources/x.css" ) );
		assertFalse( servable( "" ) );
	}
}
