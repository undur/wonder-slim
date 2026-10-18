package ajaxplayground;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

import er.extensions.appserver.ERXRequest;

/**
 * Echoes what the application concluded about where the request came from - the client's address, the host
 * and the scheme - next to the headers it concluded that from, for checking the application against the
 * front end in front of it (modulo, Apache) from outside.
 */
public class OriginEcho {

	/** The headers that carry a claim about the request's origin, as any front end or client might send them */
	private static final List<String> ORIGIN_HEADERS = List.of(
			"host", "forwarded", "x-forwarded-for", "x-forwarded-proto", "x-forwarded-host", "x-forwarded-port", "x-forwarded-server", "x-real-ip",
			"remote_addr", "remote_host", "x-webobjects-remote-addr", "x-webobjects-server-name", "x-webobjects-server-port", "https",
			"x-webobjects-servlet-server-name", "x-webobjects-servlet-server-port", "server_name", "server_port", "http_host", "pc-remote-addr",
			"remote_user", "x-webobjects-adaptor-version" );

	private OriginEcho() {}

	public static WOResponse response( final WORequest request ) {
		final StringBuilder b = new StringBuilder();

		final InetAddress connection = request._originatingAddress();
		line( b, "connection", connection == null ? null : connection.getHostAddress() );
		line( b, "isFromTrustedFrontEnd", ERXRequest.isFromTrustedFrontEnd( request ) );
		line( b, "isUsingWebServer", request.isUsingWebServer() );
		b.append( '\n' );

		line( b, "remoteAddress", ERXRequest.remoteAddress( request ) );
		line( b, "_serverName", request._serverName() );
		line( b, "isSecure", request.isSecure() );
		line( b, "isRequestSecure", ERXRequest.isRequestSecure( request ) );

		final WOContext context = request.context();
		line( b, "context.secureMode", context.secureMode() );
		line( b, "complete URL, secure as the request", context.completeURLWithRequestHandlerKey( "wa", "default", null, request.isSecure(), 0 ) );
		line( b, "directActionURLForActionNamed( name, dict )", context.directActionURLForActionNamed( "default", null ) );
		context.generateCompleteURLs();
		try {
			line( b, "complete URL, generateCompleteURLs", context.directActionURLForActionNamed( "default", null ) );
		}
		finally {
			context.generateRelativeURLs();
		}
		b.append( '\n' );

		final Map<String, List<String>> received = new TreeMap<>();
		for( final String name : ORIGIN_HEADERS ) {
			final List<String> values = request.headersForKey( name );
			if( values != null && !values.isEmpty() ) {
				received.put( name, values );
			}
		}
		b.append( "origin headers received:\n" );
		received.forEach( ( name, values ) -> values.forEach( value -> b.append( "  " ).append( name ).append( ": " ).append( value ).append( '\n' ) ) );

		final WOResponse response = new WOResponse();
		response.setHeader( "text/plain; charset=utf-8", "content-type" );
		response.setHeader( "no-store", "cache-control" );
		response.setContent( b.toString() );
		return response;
	}

	private static void line( final StringBuilder b, final String label, final Object value ) {
		b.append( label ).append( ": " ).append( value ).append( '\n' );
	}
}
