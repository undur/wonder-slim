package er.extensions.routing;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

import er.extensions.appserver.ERXApplication;
import er.extensions.foundation.ERXProperties;

/**
 * The address people reach the application at, for complete URLs (#187): set with
 * {@value #PROPERTY}, as {@code https://bookclubs.example.com} (a scheme, a host, and a port if it isn't the scheme's).
 *
 * When it's set, complete URLs (an email's links) have its scheme, host and port, a route on a host pattern gets its
 * scheme and port, and {@link Route#completeURL(Record)} works outside a request. When it isn't, the request's
 * address is used, and a complete URL outside a request is an error. Next to {@link RequestHost}, the framework's other
 * answer to "where is this", and moving with it when routing converges.
 */

final class PublicAddress {

	public static final String PROPERTY = "er.routing.publicAddress";

	/**
	 * @param scheme {@code http} or {@code https}
	 * @param host The host name, lower case
	 * @param port The port, -1 for the scheme's own
	 */
	public record Origin( String scheme, String host, int port ) {

		/**
		 * @return The port as written after a host ({@code :1300}), empty for the scheme's own
		 */
		public String portSuffix() {
			return port == -1 ? "" : ":" + port;
		}

		/**
		 * @return The scheme, host and port: {@code https://bookclubs.example.com}
		 */
		public String origin() {
			return scheme + "://" + host + portSuffix();
		}

		/**
		 * @return The scheme and port of this origin with another host: {@code https://acme.example.com}
		 */
		public String origin( final String otherHost ) {
			return scheme + "://" + otherHost + portSuffix();
		}
	}

	/**
	 * The configured address, read once (empty for none), null until it's read
	 */
	private static volatile Optional<Origin> _configured;

	private PublicAddress() {}

	/**
	 * @return The configured public address, null if there's none. Read once: the router reads it as the application
	 *         finishes launching, so a value that isn't an address stops the launch rather than the first page.
	 * @throws IllegalStateException if it's set to something that isn't one, naming the property
	 */
	public static Origin configured() {
		Optional<Origin> configured = _configured;

		if( configured == null ) {
			final String value = ERXProperties.stringForKey( PROPERTY );
			configured = Optional.ofNullable( value == null || value.isBlank() ? null : parse( value.trim() ) );
			_configured = configured;
		}

		return configured.orElse( null );
	}

	/**
	 * @return The application's domain, for host patterns relative to it ({@code {club}.@}): the public address's host,
	 *         {@code localhost} without one in development (where any {@code *.localhost} is this machine)
	 * @throws IllegalStateException without a public address outside development, naming the property
	 */
	public static String domain() {
		final Origin origin = configured();

		if( origin != null ) {
			return origin.host();
		}

		// Deployed, localhost would be a domain no request has, and every route on it would silently answer nothing
		if( com.webobjects.appserver.WOApplication.application() != null && !ERXApplication.isDevelopmentModeSafe() ) {
			throw new IllegalStateException( "A host pattern relative to the application's domain ({club}.@) needs the application's public address outside development: set %s (https://bookclubs.example.com, say)".formatted( PROPERTY ) );
		}

		return "localhost";
	}

	/**
	 * @return The configured public address
	 * @throws IllegalStateException if none is set, naming the property
	 */
	public static Origin required() {
		final Origin origin = configured();

		if( origin == null ) {
			throw new IllegalStateException( "A complete URL outside a request needs the application's public address: set %s (https://bookclubs.example.com, say)".formatted( PROPERTY ) );
		}

		return origin;
	}

	static Origin parse( final String value ) {
		final URI uri;

		try {
			uri = new URI( value );
		}
		catch( URISyntaxException e ) {
			throw invalid( value, "it isn't a URL" );
		}

		final String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase( Locale.ROOT );

		if( !"http".equals( scheme ) && !"https".equals( scheme ) ) {
			throw invalid( value, "its scheme is http or https" );
		}

		if( uri.getHost() == null ) {
			throw invalid( value, "it has a host" );
		}

		// A base path is #51's: refused rather than half supported
		if( (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals( "/" )) || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null ) {
			throw invalid( value, "it's a scheme, a host and a port only, without a path, query or user" );
		}

		final int port = uri.getPort() == defaultPort( scheme ) ? -1 : uri.getPort();
		return new Origin( scheme, uri.getHost().toLowerCase( Locale.ROOT ), port );
	}

	private static int defaultPort( final String scheme ) {
		return scheme.equals( "https" ) ? 443 : 80;
	}

	private static IllegalStateException invalid( final String value, final String rule ) {
		return new IllegalStateException( "%s is '%s', and %s".formatted( PROPERTY, value, rule ) );
	}
}
