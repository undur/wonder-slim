package er.extensions.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import er.extensions.resources.ERXAppBasedResourceRequestHandler.BoundedPathSet;

public class ERXAppBasedResourceRequestHandlerTest {

	private static int status( final String path ) {
		return new ERXAppBasedResourceRequestHandler().responseForPath( path ).status();
	}

	@Test
	public void aPathThatNamesNoResourceIsNotFound() {
		assertEquals( 404, status( null ) );
		assertEquals( 404, status( "" ) );
		assertEquals( 404, status( "foo" ) );
		assertEquals( 404, status( "/foo" ) );
		assertEquals( 404, status( "app/" ) );
		assertEquals( 404, status( "app/public/" ) );
	}

	@Test
	public void theMissingPathsAreBounded() {
		final BoundedPathSet paths = new BoundedPathSet( 3 );

		for( int i = 0; i < 100; i++ ) {
			paths.add( "app/probe" + i );
		}

		assertEquals( 3, paths.size() );
		assertTrue( paths.contains( "app/probe99" ) );
		assertFalse( paths.contains( "app/probe0" ) );
	}

	@Test
	public void theLeastRecentlyUsedPathIsForgottenFirst() {
		final BoundedPathSet paths = new BoundedPathSet( 2 );
		paths.add( "a" );
		paths.add( "b" );
		paths.contains( "a" ); // a use of "a", so "b" is now the least recently used
		paths.add( "c" );

		assertTrue( paths.contains( "a" ) );
		assertFalse( paths.contains( "b" ) );
		assertTrue( paths.contains( "c" ) );
	}
}
