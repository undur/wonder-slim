package er.extensions.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

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

	private static ERXAppBasedResourceRequestHandler.InMemoryResource resourceOf( final int size ) {
		return new ERXAppBasedResourceRequestHandler.InMemoryResource( "text/plain", new byte[size] );
	}

	@Test
	public void theCacheHoldsAtMostItsByteLimitForgettingTheLeastRecentlyUsed() {
		final ERXAppBasedResourceRequestHandler.ResourceCache cache = new ERXAppBasedResourceRequestHandler.ResourceCache( 300 );
		cache.put( "a", resourceOf( 100 ) );
		cache.put( "b", resourceOf( 100 ) );
		cache.put( "c", resourceOf( 100 ) );
		cache.get( "a" ); // a use of "a", so "b" is now the least recently used
		cache.put( "d", resourceOf( 100 ) );

		assertEquals( 300, cache.bytes() );
		assertNotNull( cache.get( "a" ) );
		assertNull( cache.get( "b" ) );
		assertNotNull( cache.get( "c" ) );
		assertNotNull( cache.get( "d" ) );
	}

	@Test
	public void replacingAResourceCountsOnlyTheNewOne() {
		final ERXAppBasedResourceRequestHandler.ResourceCache cache = new ERXAppBasedResourceRequestHandler.ResourceCache( 1000 );
		cache.put( "a", resourceOf( 100 ) );
		cache.put( "a", resourceOf( 50 ) );
		assertEquals( 50, cache.bytes() );
		assertEquals( 1, cache.size() );
	}

	@Test
	public void theCacheSizeIsGivenInMegabytes() {
		assertEquals( 64L * 1024 * 1024, ERXAppBasedResourceRequestHandler.cacheByteLimit( ERXAppBasedResourceRequestHandler.DEFAULT_CACHE_MEGABYTES ) );
		assertEquals( 0, ERXAppBasedResourceRequestHandler.cacheByteLimit( 0 ) );
		assertThrows( IllegalArgumentException.class, () -> ERXAppBasedResourceRequestHandler.cacheByteLimit( -1 ) );
	}

	@Test
	public void aCacheOfNoSizeHoldsNoContent() {
		final ERXAppBasedResourceRequestHandler.ResourceCache cache = new ERXAppBasedResourceRequestHandler.ResourceCache( 0 );
		cache.put( "a", resourceOf( 100 ) );
		assertEquals( 0, cache.bytes() );
		assertNull( cache.get( "a" ) );
	}

	@Test
	public void theBoundedStreamEndsWhereTheRangeDoes() throws IOException {
		final byte[] content = "0123456789".getBytes( StandardCharsets.US_ASCII );
		final InputStream in = new ByteArrayInputStream( content );
		in.skipNBytes( 3 );
		assertEquals( "3456", new String( new ERXAppBasedResourceRequestHandler.BoundedInputStream( in, 4 ).readAllBytes(), StandardCharsets.US_ASCII ) );
	}

	@Test
	public void aResourceIsServedWholeOrInRanges() {
		final ERXAppBasedResourceRequestHandler.InMemoryResource resource = new ERXAppBasedResourceRequestHandler.InMemoryResource( "text/plain", "0123456789".getBytes( StandardCharsets.US_ASCII ) );

		final WOResponse whole = resource.response( null, null );
		assertEquals( 200, whole.status() );
		assertEquals( "10", whole.headerForKey( "content-length" ) );

		final WOResponse part = resource.response( requestWithRange( "bytes=2-4" ), null );
		assertEquals( 206, part.status() );
		assertEquals( "bytes 2-4/10", part.headerForKey( "content-range" ) );
		assertEquals( "3", part.headerForKey( "content-length" ) );

		assertEquals( 416, resource.response( requestWithRange( "bytes=20-" ), null ).status() );
	}

	private static WORequest requestWithRange( final String range ) {
		return new WORequest( "GET", "/", "HTTP/1.1", Map.of( "range", List.of( range ) ), null, null );
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
