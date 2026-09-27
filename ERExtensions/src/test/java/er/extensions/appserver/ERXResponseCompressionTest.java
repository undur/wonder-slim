package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSRange;

public class ERXResponseCompressionTest {

	@Test
	public void compressibleTypes() {
		assertTrue( ERXResponseCompression.isCompressibleType( "text/html" ) );
		assertTrue( ERXResponseCompression.isCompressibleType( "text/html; charset=UTF-8" ) );
		assertTrue( ERXResponseCompression.isCompressibleType( "Text/CSS" ) );
		assertTrue( ERXResponseCompression.isCompressibleType( "application/json; charset=utf-8" ) );
		assertTrue( ERXResponseCompression.isCompressibleType( "application/javascript" ) );
		assertTrue( ERXResponseCompression.isCompressibleType( "image/svg+xml" ) );

		assertFalse( ERXResponseCompression.isCompressibleType( null ) );
		assertFalse( ERXResponseCompression.isCompressibleType( "image/png" ) );
		assertFalse( ERXResponseCompression.isCompressibleType( "application/octet-stream" ) );
	}

	@Test
	public void acceptEncoding() {
		assertTrue( ERXResponseCompression.acceptsGzip( "gzip" ) );
		assertTrue( ERXResponseCompression.acceptsGzip( "gzip, deflate, br" ) );
		assertTrue( ERXResponseCompression.acceptsGzip( "br;q=1.0, GZIP;q=0.8" ) );
		assertTrue( ERXResponseCompression.acceptsGzip( "x-gzip" ) );
		assertTrue( ERXResponseCompression.acceptsGzip( "*" ) );

		assertFalse( ERXResponseCompression.acceptsGzip( null ) );
		assertFalse( ERXResponseCompression.acceptsGzip( "" ) );
		assertFalse( ERXResponseCompression.acceptsGzip( "br, deflate" ) );
		assertFalse( ERXResponseCompression.acceptsGzip( "gzip;q=0" ) );
		assertFalse( ERXResponseCompression.acceptsGzip( "gzip;q=0, *" ) );
		assertFalse( ERXResponseCompression.acceptsGzip( "*;q=0" ) );
	}

	@Test
	public void contentEncoding() {
		assertTrue( ERXResponseCompression.isEncoded( "gzip" ) );
		assertTrue( ERXResponseCompression.isEncoded( "br" ) );
		assertTrue( ERXResponseCompression.isEncoded( "deflate" ) );

		assertFalse( ERXResponseCompression.isEncoded( null ) );
		assertFalse( ERXResponseCompression.isEncoded( " " ) );
		assertFalse( ERXResponseCompression.isEncoded( "identity" ) );
	}

	@Test
	public void vary() {
		assertEquals( "Accept-Encoding", ERXResponseCompression.varyIncludingAcceptEncoding( null ) );
		assertEquals( "Cookie, Accept-Encoding", ERXResponseCompression.varyIncludingAcceptEncoding( "Cookie" ) );
		assertEquals( "Cookie, accept-encoding", ERXResponseCompression.varyIncludingAcceptEncoding( "Cookie, accept-encoding" ) );
		assertEquals( "*", ERXResponseCompression.varyIncludingAcceptEncoding( "*" ) );
	}

	@Test
	public void compressesByteContent() throws IOException {
		final byte[] content = "Hello, compression. ".repeat( 200 ).getBytes();
		final WOResponse response = response( "text/html; charset=UTF-8", content );

		ERXResponseCompression.compressResponse( response );

		assertEquals( "gzip", response.headerForKey( "content-encoding" ) );
		assertArrayEquals( content, gunzip( response.content().bytes() ) );
		assertEquals( String.valueOf( response.content().length() ), response.headerForKey( "content-length" ) );
	}

	@Test
	public void compressesOnlyTheContentsRange() throws IOException {
		final byte[] backing = ("PREFIX" + "Ranged content. ".repeat( 200 ) + "SUFFIX").getBytes();
		final int length = backing.length - 12;
		final WOResponse response = new WOResponse();
		response.setHeader( "text/plain", "content-type" );
		response.setContent( new NSData( backing, new NSRange( 6, length ), true ) );

		ERXResponseCompression.compressResponse( response );

		assertEquals( "Ranged content. ".repeat( 200 ), new String( gunzip( response.content().bytes() ) ) );
	}

	@Test
	public void compressesStreamContent() throws IOException {
		final byte[] content = "Streamed. ".repeat( 500 ).getBytes();
		final WOResponse response = new WOResponse();
		response.setHeader( "text/plain", "content-type" );
		response.setContentStream( new ByteArrayInputStream( content ), 4096, content.length );

		ERXResponseCompression.compressResponse( response );

		assertNull( response.contentInputStream() );
		assertEquals( "gzip", response.headerForKey( "content-encoding" ) );
		assertArrayEquals( content, gunzip( response.content().bytes() ) );
	}

	@Test
	public void failedStreamCompressionIsAnError() {
		final WOResponse response = new WOResponse();
		response.setHeader( "text/plain", "content-type" );
		response.setContentStream( new InputStream() {
			@Override
			public int read() throws IOException {
				throw new IOException( "Broken source" );
			}
		}, 4096, 5000 );

		ERXResponseCompression.compressResponse( response );

		assertEquals( 500, response.status() );
		assertNull( response.headerForKey( "content-encoding" ) );
		assertEquals( 0, response.content().length() );
	}

	@Test
	public void applyCompressionVariesAndCompressesOnlyWhenAccepted() {
		final byte[] content = "Hello, compression. ".repeat( 200 ).getBytes();

		final WOResponse accepted = response( "text/html", content );
		ERXResponseCompression.applyCompression( request( "gzip, deflate" ), accepted );
		assertEquals( "gzip", accepted.headerForKey( "content-encoding" ) );
		assertEquals( "Accept-Encoding", accepted.headerForKey( "vary" ) );

		final WOResponse refused = response( "text/html", content );
		ERXResponseCompression.applyCompression( request( null ), refused );
		assertNull( refused.headerForKey( "content-encoding" ) );
		assertEquals( "Accept-Encoding", refused.headerForKey( "vary" ) );
		assertEquals( content.length, refused.content().length() );

		final WOResponse small = response( "text/html", "tiny".getBytes() );
		ERXResponseCompression.applyCompression( request( "gzip" ), small );
		assertNull( small.headerForKey( "content-encoding" ) );
		assertNull( small.headerForKey( "vary" ) );

		final WOResponse alreadyEncoded = response( "text/html", content );
		alreadyEncoded.setHeader( "br", "content-encoding" );
		ERXResponseCompression.applyCompression( request( "gzip" ), alreadyEncoded );
		assertEquals( "br", alreadyEncoded.headerForKey( "content-encoding" ) );
		assertEquals( content.length, alreadyEncoded.content().length() );
	}

	private static WORequest request( final String acceptEncoding ) {
		final Map<String, List<String>> headers = new HashMap<>();

		if( acceptEncoding != null ) {
			headers.put( "accept-encoding", List.of( acceptEncoding ) );
		}

		return new WORequest( "GET", "/", "HTTP/1.1", headers, null, null );
	}

	private static WOResponse response( final String contentType, final byte[] content ) {
		final WOResponse response = new WOResponse();
		response.setHeader( contentType, "content-type" );
		response.setContent( new NSData( content ) );
		return response;
	}

	private static byte[] gunzip( final byte[] bytes ) throws IOException {
		try( GZIPInputStream in = new GZIPInputStream( new ByteArrayInputStream( bytes ) ) ) {
			return in.readAllBytes();
		}
	}
}
