package er.extensions.projectlayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.development.NSResourceType;

public class ERXProjectLayoutBundleTest {

	@TempDir
	Path temporaryFolder;

	Path projectFolder;

	@BeforeEach
	public void resolveRealPath() throws IOException {
		projectFolder = temporaryFolder.toRealPath();
	}

	private NSBundle bundleFor( final String buildProperties ) throws IOException {
		Files.writeString( projectFolder.resolve( "build.properties" ), buildProperties );
		return new ERXProjectLayoutBundleFactory().createBundleFromProjectFolder( projectFolder.toFile() );
	}

	@Test
	public void folderThatIsNotAProjectIsLeftToOtherFactories() throws IOException {
		assertNull( bundleFor( "project.name=App\n" ) );
		assertNull( bundleFor( "bin.includes=META-INF/\n" ) );
	}

	@Test
	public void projectWithoutDeclaredLayoutGetsTheMavenLayout() throws IOException {
		final ERXProjectLayoutBundle bundle = (ERXProjectLayoutBundle)bundleFor( "project.name=App\nproject.type=application\n" );
		assertEquals( List.of( "src/main/components", "src/main/woresources", "src/main/webserver-resources" ), bundle.relativePathForResourceType( NSResourceType.Component ) );
	}

	@Test
	public void projectWithoutBuildPropertiesIsLeftToOtherFactories() {
		assertNull( new ERXProjectLayoutBundleFactory().createBundleFromProjectFolder( projectFolder.toFile() ) );
	}

	@Test
	public void builtBundlesAreLeftToOtherFactories() throws IOException {
		final Path woa = Files.createDirectories( projectFolder.resolve( "App.woa" ) );
		Files.writeString( woa.resolve( "build.properties" ), "dir.components=Components\n" );
		assertNull( new ERXProjectLayoutBundleFactory().createBundleFromProjectFolder( woa.toFile() ) );
	}

	@Test
	public void eachTypeLooksInItsOwnFolderFirst() throws IOException {
		final ERXProjectLayoutBundle bundle = (ERXProjectLayoutBundle)bundleFor( "project.name=App\nproject.type=application\ndir.components=Components\ndir.woresources=Resources\ndir.webserverResources=WebServerResources\n" );
		assertEquals( List.of( "Components", "Resources", "WebServerResources" ), bundle.relativePathForResourceType( NSResourceType.Component ) );
		assertEquals( List.of( "Resources", "Components", "WebServerResources" ), bundle.relativePathForResourceType( NSResourceType.Model ) );
		assertEquals( List.of( "WebServerResources", "Resources", "Components" ), bundle.relativePathForResourceType( NSResourceType.WebServer ) );
		assertEquals( List.of( "Resources", "Components", "WebServerResources" ), bundle.relativePathForResourceType( NSResourceType.Other ) );
	}

	@Test
	public void resourcesAreFoundInTheDeclaredFolders() throws IOException {
		Files.createDirectories( projectFolder.resolve( "Components/Main.wo" ) );
		Files.createDirectories( projectFolder.resolve( "Resources" ) );
		Files.writeString( projectFolder.resolve( "Resources/Settings.plist" ), "{}" );
		Files.createDirectories( projectFolder.resolve( "WebServerResources" ) );
		Files.writeString( projectFolder.resolve( "WebServerResources/app.css" ), "" );

		final NSBundle bundle = bundleFor( "project.name=App\nproject.type=application\ndir.components=Components\ndir.woresources=Resources\ndir.webserverResources=WebServerResources\n" );
		assertInstanceOf( ERXProjectLayoutBundle.class, bundle );
		assertEquals( "App", bundle.name() );
		assertEquals( projectFolder.resolve( "Components/Main.wo" ), fileFor( bundle, "Main.wo" ) );
		assertEquals( projectFolder.resolve( "Resources/Settings.plist" ), fileFor( bundle, "Settings.plist" ) );
		assertEquals( projectFolder.resolve( "WebServerResources/app.css" ), fileFor( bundle, "app.css" ) );
		assertNull( bundle.resourcePathForLocalizedResourceNamed( "Missing.wo", null ) );
	}

	@Test
	public void undeclaredFoldersAreNotSearched() throws IOException {
		Files.createDirectories( projectFolder.resolve( "src/main/resources/App.eomodeld" ) );
		Files.writeString( projectFolder.resolve( "src/main/resources/Properties" ), "" );

		final NSBundle bundle = bundleFor( "project.name=App\nproject.type=application\n" );
		assertNull( bundle.resourcePathForLocalizedResourceNamed( "App.eomodeld", null ) );
		assertNull( bundle.resourcePathForLocalizedResourceNamed( "Properties", null ) );
	}

	/**
	 * @return The file NSBundle resolves the named resource to
	 */
	private static Path fileFor( final NSBundle bundle, final String resourceName ) {
		final String resourcePath = bundle.resourcePathForLocalizedResourceNamed( resourceName, null );
		assertNotNull( resourcePath, resourceName + " not found" );

		try {
			return Path.of( bundle.pathURLForResourcePath( resourcePath ).toURI() ).toRealPath();
		}
		catch( final Exception e ) {
			throw new AssertionError( e );
		}
	}
}
