package er.extensions.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class ERXResourceStampsTest {

	private static final String STAMP = "3f9c1e07ab";

	@Test
	public void theStampIsTenHexDigitsOfTheContent() {
		final String stamp = ERXResourceStamps.stamp( "body {}".getBytes( StandardCharsets.UTF_8 ) );
		assertEquals( 10, stamp.length() );
		assertEquals( stamp, ERXResourceStamps.stamp( "body {}".getBytes( StandardCharsets.UTF_8 ) ) );
		assertNotEquals( stamp, ERXResourceStamps.stamp( "body { }".getBytes( StandardCharsets.UTF_8 ) ) );
	}

	@Test
	public void theStampGoesBeforeTheExtension() {
		assertEquals( "site.3f9c1e07ab.css", ERXResourceStamps.stampedPath( "site.css", STAMP ) );
		assertEquals( "css/site.3f9c1e07ab.css", ERXResourceStamps.stampedPath( "css/site.css", STAMP ) );
		assertEquals( "archive.tar.3f9c1e07ab.gz", ERXResourceStamps.stampedPath( "archive.tar.gz", STAMP ) );
		assertEquals( "LICENSE.3f9c1e07ab", ERXResourceStamps.stampedPath( "LICENSE", STAMP ) );
		assertEquals( "x/.htaccess.3f9c1e07ab", ERXResourceStamps.stampedPath( "x/.htaccess", STAMP ) );
	}

	@Test
	public void aStampedPathParsesBackToThePathAndTheStamp() {
		for( final String path : new String[] { "site.css", "app/css/site.css", "archive.tar.gz", "LICENSE", "x/.htaccess", "my.file.name.js" } ) {
			final ERXResourceStamps.Stamped stamped = ERXResourceStamps.parse( ERXResourceStamps.stampedPath( path, STAMP ) );
			assertEquals( path, stamped.unstampedPath() );
			assertEquals( STAMP, stamped.stamp() );
		}
	}

	@Test
	public void anUnstampedPathHasNoStamp() {
		assertNull( ERXResourceStamps.parse( "app/css/site.css" ) );
		assertNull( ERXResourceStamps.parse( "AjaxSlim/ajaxslim.js" ) );
		assertNull( ERXResourceStamps.parse( "app/x.3F9C1E07AB.css" ) ); // uppercase isn't a stamp
		assertNull( ERXResourceStamps.parse( "app/x.3f9c1e07a.css" ) ); // nine digits isn't one
		assertNull( ERXResourceStamps.parse( "app/3f9c1e07ab.css" ) ); // nothing before it: the name itself
		assertNull( ERXResourceStamps.parse( "app/x.3f9c1e07ab/y.css" ) ); // in a folder name, not the file's
	}
}
