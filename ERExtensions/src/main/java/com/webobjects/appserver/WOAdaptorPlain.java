package com.webobjects.appserver;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
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
 * A WOAdaptor based on Java's built in HTTP server
 *
 * To use, set the property -WOAdaptor WOAdaptorPlain
 */

public class WOAdaptorPlain extends WOAdaptor {

	private static final Logger logger = LoggerFactory.getLogger( WOAdaptorPlain.class );

	/**
	 * Invoked by WO to construct an adaptor instance
	 */
	public WOAdaptorPlain( String name, NSDictionary<String, Object> config ) {
		super( name, config );
		_port = port( config );

		// If the port is occupied, we emulate WO's behaviour. Helps ERXApplication handle the exception, restarting any app occupying the port
		if( !isPortAvailable( _port ) ) {
			throw new NSForwardException( new BindException( "Port %s is occupied".formatted( _port ) ) );
		}

		try {
			// Copied from the Netty adaptor
			WOApplication.application()._setHost( InetAddress.getLocalHost().getHostName() );
			System.setProperty( WOProperties._PortKey, Integer.toString( _port ) );
		}
		catch( UnknownHostException e ) {
			throw new RuntimeException( e );
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

	/**
	 * @return true if the given port is available for us to use
	 */
	private static boolean isPortAvailable( int port ) {
		try( ServerSocket socket = new ServerSocket( port )) {
			return true;
		}
		catch( IOException e ) {
			return false;
		}
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

		public void handle( HttpExchange exchange ) throws IOException {

			// Check for chunked transfer encoding - we don't support it (our current handling of the request's content requires known content-length)
			// If this is actually required we could add support  for it
			final List<String> transferEncoding = exchange.getRequestHeaders().get( "Transfer-Encoding" );

			if( transferEncoding != null && transferEncoding.stream().anyMatch( s -> s.toLowerCase().contains( "chunked" ) ) ) {
				logger.warn( "Received chunked transfer-encoded request to {} - not supported. Returning 411 Length Required", exchange.getRequestURI() );
				exchange.sendResponseHeaders( 411, -1 ); // 411 Length Required
				exchange.close();
				return;
			}

			final WORequest woRequest = requestToWORequest( exchange );

			// This is where the application logic will perform it's actual work
			final WOResponse woResponse = WOApplication.application().dispatchRequest( woRequest );

			exchange.getResponseHeaders().putAll( woResponse.headers() );

			if( woResponse.contentInputStream() != null ) {
				final long contentLength = woResponse.contentInputStreamLength(); // If an InputStream is present, the stream's length must be present as well

				if( contentLength == -1 ) {
					throw new IllegalArgumentException( "WOResponse.contentInputStream() is set but contentInputLength has not been set. You must provide the content length when serving an InputStream" );
				}

				exchange.sendResponseHeaders( woResponse.status(), contentLength );

				try( final InputStream inputStream = woResponse.contentInputStream()) {
					try( final OutputStream out = exchange.getResponseBody()) {
						inputStream.transferTo( out );
					}
				}
			}
			else {
				final long contentLength = woResponse.content()._bytesNoCopy().length;
				exchange.sendResponseHeaders( woResponse.status(), contentLength );

				try( final OutputStream out = exchange.getResponseBody()) {
					new ByteArrayInputStream( woResponse.content()._bytesNoCopy() ).transferTo( out );
				}
			}
		}

		/**
		 * @return the given Request converted to a WORequest
		 */
		private static WORequest requestToWORequest( final HttpExchange exchange ) {

			final String method = exchange.getRequestMethod();
			final String uri = exchange.getRequestURI().toString();
			final String httpVersion = exchange.getProtocol();
			final Map<String, List<String>> headers = exchange.getRequestHeaders();

			final NSData contentData;

			final int contentLength = contentLength( headers );

			if (contentLength  > 0) {
				logger.info( "Constructing streaming request content with length: " + contentLength );
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
		 * Obtain information about originating address/target address at set on the given request 
		 */
		private static void populateAddresses(final HttpExchange exchange, final WORequest request) {
		    final InetSocketAddress remote = exchange.getRemoteAddress();
		    final InetSocketAddress local  = exchange.getLocalAddress();

		    request._setOriginatingAddress(remote.getAddress());
		    request._setOriginatingPort(remote.getPort());

		    request._setAcceptingAddress(local.getAddress());
		    request._setAcceptingPort(local.getPort());
		}
	}
}