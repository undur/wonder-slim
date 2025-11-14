package com.webobjects.appserver;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.webobjects.appserver._private.WOInputStreamData;
import com.webobjects.appserver._private.WONoCopyPushbackInputStream;
import com.webobjects.appserver._private.WOProperties;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;

/**
 * A WOAdaptor based on Java's built in HTTP server. To use, set the property -WOAdaptor WOAdaptorPlain
 */

public class WOAdaptorPlain extends WOAdaptor {

	private static final Logger logger = LoggerFactory.getLogger( WOAdaptorPlain.class );

	/**
	 * Invoked by WO to construct an adaptor instance
	 */
	public WOAdaptorPlain( String name, NSDictionary<String, Object> config ) {
		super( name, config );
		_port = port( config );

		checkPortAvailable(_port);

		try {
			// Set-The-Port-Stuff copied from the WONettyAdaptor, feels a little dirty
			WOApplication.application()._setHost( InetAddress.getLocalHost().getHostName() );
			System.setProperty( WOProperties._PortKey, Integer.toString( _port ) );
		}
		catch( UnknownHostException e ) {
			throw new UncheckedIOException( e );
		}
	}

	/**
	 * Briefly try binding to the requested port. If unsuccessful, emulate WO's behaviour (wrap the BindException in NSForwardException) to help ERXApplication catch it and stop any apps occupying the port 
	 */
	private static void checkPortAvailable( final int port ) {
		try( ServerSocket socket = new ServerSocket( port )) {}
		catch( IOException e ) {
			throw new NSForwardException( e );
		}
	}

	/**
	 * @return the port we'll be listening on
	 */
	private static int port( NSDictionary<String, Object> config ) {
		final Number number = (Number)config.objectForKey( WOProperties._PortKey );

		int port = 0;

		if( number != null ) {
			port = number.intValue();
		}

		if( port < 0 ) {
			port = 0;
		}

		return port;
	}

	@Override
	public boolean dispatchesRequestsConcurrently() {
		return true;
	}

	@Override
	public void unregisterForEvents() {
		// FIXME: Missing implementation // Hugi 2025-11-11
		logger.error( "We haven't implemented WOAdaptor.unregisterForEvents()" );
	}

	@Override
	public void registerForEvents() {
		try {
			logger.info( "Starting %s on port %s".formatted( getClass().getSimpleName(), _port ) );
			var server = HttpServer.create( new InetSocketAddress( _port ), 0 );
			server.setExecutor( Executors.newVirtualThreadPerTaskExecutor() );
			server.createContext( "/" ).setHandler( new WOHandler() );
			server.start();
		}
		catch( final Exception e ) {
			e.printStackTrace();
			System.exit( -1 );
		}
	}

	public static class WOHandler implements HttpHandler {

		public void handle( final HttpExchange exchange ) throws IOException {

			// If the request has Transfer-Encoding: chunked we currently just reject it
			if( rejectChunkedTransferEncoding( exchange ) ) {
				return;
			}

			final WORequest request = requestFromExchange( exchange );

			// This is where the application logic will perform it's actual work
			final WOResponse response = WOApplication.application().dispatchRequest( request );

			exchange.getResponseHeaders().putAll( response.headers() );

			if( response.contentInputStream() != null ) {
				final long contentLength = response.contentInputStreamLength(); // If an InputStream is present, the stream's length must be present as well

				if( contentLength == -1 ) {
					throw new IllegalArgumentException( "WOResponse.contentInputStream() is set but contentInputLength has not been set. You must provide the content length when serving an InputStream" );
				}

				exchange.sendResponseHeaders( response.status(), contentLength );

				try( final InputStream inputStream = response.contentInputStream()) {
					try( final OutputStream out = exchange.getResponseBody()) {
						inputStream.transferTo( out );
					}
				}
			}
			else {
				final NSData responseContent = response.content();
				final long contentLength = responseContent.length();

				exchange.sendResponseHeaders( response.status(), contentLength );

				try( final OutputStream out = exchange.getResponseBody()) {
					responseContent.writeToStream( out );
				}
			}
		}

		/**
		 * @return the given Request converted to a WORequest
		 */
		private static WORequest requestFromExchange( final HttpExchange exchange ) {

			final String method = exchange.getRequestMethod();
			final String uri = exchange.getRequestURI().toString();
			final String httpVersion = exchange.getProtocol();
			final Map<String, List<String>> headers = exchange.getRequestHeaders();

			final NSData contentData;

			final int contentLength = contentLength( headers );

			if (contentLength  > 0) {
				final InputStream requestStream = exchange.getRequestBody();
				final InputStream bufferedStream = new BufferedInputStream(requestStream);
				final WONoCopyPushbackInputStream wrappedStream = new WONoCopyPushbackInputStream(bufferedStream, contentLength);
				contentData = new WOInputStreamData(wrappedStream, contentLength);
			}
			else {
				contentData = NSData.EmptyData;
			}

			final WORequest worequest = WOApplication.application().createRequest( method, uri, httpVersion, headers, contentData, null );

			populateAddresses(exchange, worequest);

			return worequest;
		}
		
		/**
		 * @return The value of the content-length header or 0 (zero) if not present 
		 */
		private static int contentLength( final Map<String, List<String>> headers ) {

			final List<String> contentLengthHeaders = headers.get("Content-Length");

			if (contentLengthHeaders != null && !contentLengthHeaders.isEmpty()) {
				return Integer.parseInt( contentLengthHeaders.getFirst() );
			}
			
			return 0;
		}

		/**
		 * Get originating/target addresses from the HttpExchange and set them on the given request 
		 */
		private static void populateAddresses(final HttpExchange exchange, final WORequest request) {
		    final InetSocketAddress remote = exchange.getRemoteAddress();
		    final InetSocketAddress local  = exchange.getLocalAddress();

		    request._setOriginatingAddress(remote.getAddress());
		    request._setOriginatingPort(remote.getPort());

		    request._setAcceptingAddress(local.getAddress());
		    request._setAcceptingPort(local.getPort());
		}
		
		/**
		 * Check for chunked transfer encoding - we don't support it (our current handling of the request's content requires known content-length)
		 */
		private boolean rejectChunkedTransferEncoding( final HttpExchange exchange ) throws IOException {
			final List<String> transferEncoding = exchange.getRequestHeaders().get( "Transfer-Encoding" );

			if( transferEncoding != null && transferEncoding.stream().anyMatch( s -> s.toLowerCase().contains( "chunked" ) ) ) {
				logger.warn( "Received chunked transfer-encoded request to {} - not supported. Returning 411 Length Required", exchange.getRequestURI() );
				exchange.sendResponseHeaders( 411, -1 ); // 411 Length Required
				exchange.close();
				return true;
			}
			
			return false;
		}
	}
}