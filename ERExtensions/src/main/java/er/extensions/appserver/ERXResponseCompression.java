package er.extensions.appserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSMutableRange;
import com.webobjects.foundation.NSRange;

import er.extensions.ERXP;
import er.extensions.foundation.ERXProperties;

/**
 * Hosts the response compression logic previously found in ERXApplication 
 */

public class ERXResponseCompression {

	private static final Logger log = LoggerFactory.getLogger(ERXResponseCompression.class);

	private static Set<String> _responseCompressionTypes;
	private static Boolean _responseCompressionEnabled;

	/**
	 * Responses smaller than this aren't worth compressing: the gzip header and the work outweigh the savings, and the
	 * result can be larger than the original.
	 */
	private static final int MINIMUM_SIZE = 1024;

	/**
	 * Content types compressed by default, in addition to every {@code text/*} type
	 */
	private static final NSArray<String> DEFAULT_COMPRESSION_TYPES = new NSArray<>( new String[] { "application/javascript", "application/json", "application/xml", "image/svg+xml" } );

	/**
	 * @return The content types compressed in addition to every {@code text/*} type, lowercase. See {@link ERXP#RESPONSE_COMPRESSION_TYPES}.
	 */
	public static Set<String> responseCompressionTypes() {
		if( _responseCompressionTypes == null ) {
			final Set<String> types = new HashSet<>();

			for( final String type : ERXProperties.arrayForKeyWithDefault( ERXP.RESPONSE_COMPRESSION_TYPES.id(), DEFAULT_COMPRESSION_TYPES ) ) {
				types.add( type.trim().toLowerCase( Locale.ROOT ) );
			}

			_responseCompressionTypes = Set.copyOf( types );
		}

		return _responseCompressionTypes;
	}

	/**
	 * @return true if response compression is turned on. See {@link ERXP#RESPONSE_COMPRESSION_ENABLED}.
	 */
	public static boolean responseCompressionEnabled() {
		if( _responseCompressionEnabled == null ) {
			_responseCompressionEnabled = ERXProperties.booleanForKeyWithDefault( ERXP.RESPONSE_COMPRESSION_ENABLED.id(), false );
		}

		return _responseCompressionEnabled;
	}

	/**
	 * Compresses the response if it's worth compressing and the client accepts gzip. A response that's worth
	 * compressing also gets {@code Vary: Accept-Encoding}, whether or not this client accepts gzip, since the
	 * response differs by that header and a cache in between must know.
	 */
	public static void applyCompression( final WORequest request, final WOResponse response ) {
		if( isCompressible( response ) ) {
			response.setHeader( varyIncludingAcceptEncoding( response.headerForKey( "vary" ) ), "vary" );

			if( acceptsGzip( request.headerForKey( "accept-encoding" ) ) ) {
				compressResponse( response );
			}
		}
	}

	/**
	 * @return true if the response is worth compressing and the client accepts gzip
	 */
	public static boolean shouldCompress( final WORequest request, final WOResponse response ) {
		return isCompressible( response ) && acceptsGzip( request.headerForKey( "accept-encoding" ) );
	}

	/**
	 * @return true if the response is worth compressing, regardless of what the client accepts
	 */
	private static boolean isCompressible( final WOResponse response ) {

		// A content stream of unknown length (length 0 with a stream present) is open-ended - server-sent events, for
		// instance. Compressing would mean reading it to its end first, which never comes. Leave such responses alone.
		if( response.contentInputStream() != null && response.contentInputStreamLength() <= 0 ) {
			return false;
		}

		// Already encoded, gzip or otherwise
		if( isEncoded( response.headerForKey( "content-encoding" ) ) ) {
			return false;
		}

		if( !isCompressibleType( response.headerForKey( "content-type" ) ) ) {
			return false;
		}

		final long length = response.contentInputStream() != null ? response.contentInputStreamLength() : response.content().length();
		return length >= MINIMUM_SIZE;
	}

	/**
	 * @return true if the given Content-Encoding header value says the content is encoded
	 */
	static boolean isEncoded( final String contentEncoding ) {
		return contentEncoding != null && !contentEncoding.isBlank() && !contentEncoding.trim().equalsIgnoreCase( "identity" );
	}

	/**
	 * @return true if the given Content-Type header value is a {@code text/*} type or one of {@link #responseCompressionTypes()}. Parameters (such as {@code charset}) and case are ignored.
	 */
	static boolean isCompressibleType( final String contentType ) {
		if( contentType == null ) {
			return false;
		}

		final int semicolon = contentType.indexOf( ';' );
		final String mimeType = (semicolon == -1 ? contentType : contentType.substring( 0, semicolon )).trim().toLowerCase( Locale.ROOT );
		return mimeType.startsWith( "text/" ) || responseCompressionTypes().contains( mimeType );
	}

	/**
	 * @return true if the given Accept-Encoding header value accepts gzip: {@code gzip}, {@code x-gzip} or {@code *} with a nonzero quality, and gzip not explicitly refused with {@code q=0}
	 */
	static boolean acceptsGzip( final String acceptEncoding ) {
		if( acceptEncoding == null ) {
			return false;
		}

		Boolean gzip = null;
		boolean wildcard = false;

		for( final String element : acceptEncoding.split( "," ) ) {
			final String[] parts = element.split( ";" );
			final String coding = parts[0].trim().toLowerCase( Locale.ROOT );
			final boolean accepted = quality( parts ) > 0;

			if( coding.equals( "gzip" ) || coding.equals( "x-gzip" ) ) {
				gzip = accepted;
			}
			else if( coding.equals( "*" ) ) {
				wildcard = accepted;
			}
		}

		return gzip != null ? gzip : wildcard;
	}

	/**
	 * @return The quality ({@code q=}) of an Accept-Encoding element split at its semicolons: 1 if unspecified, 0 if unparseable
	 */
	private static double quality( final String[] parts ) {
		for( int i = 1; i < parts.length; i++ ) {
			final String parameter = parts[i].trim();

			if( parameter.toLowerCase( Locale.ROOT ).startsWith( "q=" ) ) {
				try {
					return Double.parseDouble( parameter.substring( 2 ).trim() );
				}
				catch( NumberFormatException e ) {
					return 0;
				}
			}
		}

		return 1;
	}

	/**
	 * @return The given Vary header value with {@code Accept-Encoding} added, unless it's already there (or the value is {@code *})
	 */
	static String varyIncludingAcceptEncoding( final String vary ) {
		if( vary == null || vary.isBlank() ) {
			return "Accept-Encoding";
		}

		for( final String header : vary.split( "," ) ) {
			final String name = header.trim();

			if( name.equals( "*" ) || name.equalsIgnoreCase( "accept-encoding" ) ) {
				return vary;
			}
		}

		return vary + ", Accept-Encoding";
	}

	/**
	 * Replaces the response's content with its gzipped version. The response is only modified once compression has
	 * succeeded. If compressing byte content fails, the response goes out uncompressed. If compressing a content
	 * stream fails, the stream has already been read, so the response becomes an empty 500.
	 */
	public static void compressResponse( final WOResponse response ) {
		final long start = System.currentTimeMillis();
		final InputStream contentInputStream = response.contentInputStream();
		final long inputBytesLength;
		final NSData compressedData;

		if( contentInputStream != null ) {
			inputBytesLength = response.contentInputStreamLength();

			try {
				compressedData = gzip( contentInputStream );
			}
			catch( IOException e ) {
				log.error( "Failed to compress a content stream of {} bytes. The stream has been read, so the response can't be sent; answering 500.", inputBytesLength, e );
				response.setContentStream( null, 0, 0 );
				response.setContent( NSData.EmptyData );
				response.setStatus( 500 );
				return;
			}

			response.setContentStream( null, 0, 0 );
		}
		else {
			final NSData input = response.content();
			inputBytesLength = input.length();

			if( inputBytesLength == 0 ) {
				return;
			}

			// bytesNoCopy() with a range reads the content in place. NSData.stream() would copy content set with
			// setContent(), and _bytesNoCopy() without a range ignores where the bytes start in the backing array.
			final NSMutableRange range = new NSMutableRange();
			final byte[] bytes = input.bytesNoCopy( range );

			try {
				compressedData = gzip( new ByteArrayInputStream( bytes, range.location(), range.length() ) );
			}
			catch( IOException e ) {
				log.error( "Failed to compress {} bytes of content. Sending it uncompressed.", inputBytesLength, e );
				return;
			}
		}

		response.setContent( compressedData );
		response.setHeader( String.valueOf( compressedData.length() ), "content-length" );
		response.setHeader( "gzip", "content-encoding" );

		log.debug( "before: {}, after {}, time: {}", inputBytesLength, compressedData.length(), System.currentTimeMillis() - start );
	}

	/**
	 * @return The input's content, gzipped. The input is closed.
	 */
	private static NSData gzip( final InputStream input ) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try( input; GZIPOutputStream gzip = new GZIPOutputStream( bytes ) ) {
			input.transferTo( gzip );
		}

		final byte[] compressed = bytes.toByteArray();
		return new NSData( compressed, new NSRange( 0, compressed.length ), true );
	}
}
