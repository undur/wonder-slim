package com.webobjects.appserver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
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

			try {
				// FIXME: We're reading the entire NSData here, that shouldn't be required // Hugi 2025-11-11
				contentData = new WOInputStreamData( new NSData( exchange.getRequestBody(), 4096 ) );
			}
			catch( IOException e ) {
				throw new UncheckedIOException( e );
			}

			final WORequest worequest = WOApplication.application().createRequest( method, uri, httpVersion, headers, contentData, null );

			populateAddresses(exchange, worequest);

			return worequest;
		}
		
		static void populateAddresses(final HttpExchange exchange, final WORequest aRequest) {
		    InetSocketAddress remote = exchange.getRemoteAddress();
		    InetSocketAddress local  = exchange.getLocalAddress();

		    aRequest._setOriginatingAddress(remote.getAddress());
		    aRequest._setOriginatingPort(remote.getPort());

		    aRequest._setAcceptingAddress(local.getAddress());
		    aRequest._setAcceptingPort(local.getPort());
		}
	}
}