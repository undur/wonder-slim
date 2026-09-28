package er.extensions.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import er.extensions.resources.ERXAppBasedResourceRequestHandler.BoundedPathSet;

public class ERXAppBasedResourceRequestHandlerTest {

	private static int status( final String path ) {
		return new ERXAppBasedResourceRequestHandler().responseForPath( path, null ).status();
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

	private static String range( final String header, final long length ) {
		final long[] range = ERXAppBasedResourceRequestHandler.range( header, length );
		return range == null ? "whole" : range.length == 0 ? "unsatisfiable" : range[0] + "-" + range[1];
	}

	@Test
	public void aByteRangeNamesItsFirstAndLastByte() {
		assertEquals( "0-99", range( "bytes=0-99", 1000 ) );
		assertEquals( "500-999", range( "bytes=500-", 1000 ) );
		assertEquals( "900-999", range( "bytes=-100", 1000 ) );
		assertEquals( "0-999", range( "bytes=-5000", 1000 ) ); // a suffix longer than the resource is the whole of it
		assertEquals( "900-999", range( "bytes=900-5000", 1000 ) ); // the last byte is capped at the resource's end
		assertEquals( "0-0", range( "bytes=0-0", 1000 ) );
	}

	@Test
	public void aRangePastTheEndIsUnsatisfiable() {
		assertEquals( "unsatisfiable", range( "bytes=1000-", 1000 ) );
		assertEquals( "unsatisfiable", range( "bytes=2000-3000", 1000 ) );
		assertEquals( "unsatisfiable", range( "bytes=-0", 1000 ) );
		assertEquals( "unsatisfiable", range( "bytes=0-", 0 ) );
	}

	@Test
	public void anythingElseIsAnsweredWithTheWholeResource() {
		assertEquals( "whole", range( null, 1000 ) );
		assertEquals( "whole", range( "bytes=-", 1000 ) );
		assertEquals( "whole", range( "bytes=500-100", 1000 ) ); // an invalid range is ignored
		assertEquals( "whole", range( "bytes=0-99,200-299", 1000 ) ); // several ranges
		assertEquals( "whole", range( "items=0-99", 1000 ) );
		assertEquals( "whole", range( "bytes=99999999999999999999-", 1000 ) );
	}

	@Test
	public void ifNoneMatchNamesTheCurrentEntityTag() {
		final String etag = "\"3f9c1e07ab\"";
		assertTrue( ERXAppBasedResourceRequestHandler.matchesAny( "\"3f9c1e07ab\"", etag ) );
		assertTrue( ERXAppBasedResourceRequestHandler.matchesAny( "W/\"3f9c1e07ab\"", etag ) ); // compared weakly
		assertTrue( ERXAppBasedResourceRequestHandler.matchesAny( "\"0000000000\", \"3f9c1e07ab\"", etag ) );
		assertTrue( ERXAppBasedResourceRequestHandler.matchesAny( "*", etag ) );
		assertFalse( ERXAppBasedResourceRequestHandler.matchesAny( "\"0000000000\"", etag ) );
		assertFalse( ERXAppBasedResourceRequestHandler.matchesAny( "3f9c1e07ab", etag ) ); // unquoted isn't the tag
		assertFalse( ERXAppBasedResourceRequestHandler.matchesAny( null, etag ) );
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
