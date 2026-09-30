package er.extensions.projectlayout;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Properties;

import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSValueUtilities;
import com.webobjects.foundation.development.NSStandardProjectBundle;

/**
 * Creates a {@link ERXProjectLayoutBundle} for a project folder, one whose build.properties names the project and gives
 * its type (see {@link ERXProjectLayout#isProject(Properties)}). Any other folder gets null, leaving it to the other bundle
 * factories.
 *
 * Like WebObjects' own project bundles, only when NSProjectBundleEnabled is set, which is what running from a project
 * folder means. It's read when bundles are looked up, not when the factory is registered, since ERXApplication registers
 * the factory before it sets the flag.
 *
 * Registered with {@link ERXProjectLayout#register()}, or by naming it in the NSBundleFactories system property.
 */
public class ERXProjectLayoutBundleFactory extends NSStandardProjectBundle.Factory {

	@Override
	public NSBundle bundleForPath( final String path, final boolean shouldCreateBundle, final boolean newIsJar ) {
		if( !projectBundlesEnabled() ) {
			return null;
		}

		return super.bundleForPath( path, shouldCreateBundle, newIsJar );
	}

	/**
	 * @return true if NSProjectBundleEnabled is set, read as NSBundle reads it
	 */
	static boolean projectBundlesEnabled() {
		return NSValueUtilities.booleanValue( System.getProperty( "NSProjectBundleEnabled" ) );
	}

	@Override
	protected NSBundle createBundleFromProjectFolder( final File projectFolder ) {
		final String folderName = projectFolder.getName();

		// A built bundle, not a project
		if( folderName.endsWith( ".framework" ) || folderName.endsWith( ".woa" ) ) {
			return null;
		}

		final File buildPropertiesFile = new File( projectFolder, "build.properties" );

		if( !buildPropertiesFile.isFile() ) {
			return null;
		}

		final Properties buildProperties = new Properties();

		try( InputStream stream = Files.newInputStream( buildPropertiesFile.toPath() ) ) {
			buildProperties.load( stream );
		}
		catch( final IOException e ) {
			throw new UncheckedIOException( "Failed to read " + buildPropertiesFile, e );
		}

		if( !ERXProjectLayout.isProject( buildProperties ) ) {
			return null;
		}

		return new ERXProjectLayoutBundle( projectFolder.getPath(), buildProperties, ERXProjectLayout.fromBuildProperties( buildProperties ) );
	}
}
