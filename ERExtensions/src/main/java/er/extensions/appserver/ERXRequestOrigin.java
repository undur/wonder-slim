package er.extensions.appserver;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.webobjects.appserver.WORequest;

import er.extensions.ERXP;
import er.extensions.foundation.ERXProperties;

/**
 * Where a request came from and was addressed to: the client's address, the host it asked for, and whether it came over
 * https. One rule for the whole framework (#67, the application side of undur/modulo#14).
 *
 * A front end (modulo, Apache with mod_WebObjects, a load balancer) passes these on in headers, which a client can also
 * send. So they're believed only when the request's connection comes from a trusted front end: by default loopback and
 * private networks, where front ends run, configured with {@value #TRUSTED_FRONT_ENDS_PROPERTY} (addresses and networks,
 * {@code 10.1.2.3, 192.168.0.0/16}). From anyone else, and when the connection's address isn't known, the request is
 * taken as it arrived: its connection's address, its {@code Host}, and plain http. From a trusted front end, first match
 * wins:
 *
 * <ul>
 * <li>Client address: {@code x-webobjects-remote-addr}, then the last entry of {@code x-forwarded-for}, then the
 * connection</li>
 * <li>Host: {@code x-webobjects-server-name}, then {@code x-forwarded-host}, then {@code Host}</li>
 * <li>https: the {@code https} header {@code on}, then {@code x-forwarded-proto}, then the port the front end reports
 * ({@code 443})</li>
 * </ul>
 *
 * The WO adaptor's own headers come first, since mod_WebObjects sets them and never sets {@code X-Forwarded-*}.
 */

public final class ERXRequestOrigin {

	/**
	 * The front ends whose headers are believed: addresses and networks, separated by commas
	 */
	public static final String TRUSTED_FRONT_ENDS_PROPERTY = "er.extensions.ERXRequest.trustedFrontEnds";

	/**
	 * Loopback and private networks: where a front end on the same machine or network connects from
	 */
	private static final String DEFAULT_TRUSTED_FRONT_ENDS = "127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, ::1/128, fc00::/7";

	private static volatile List<Network> _trusted;

	private ERXRequestOrigin() {}

	/**
	 * @return The address of the client that sent the request, null if none is known
	 */
	public static String clientAddress( final WORequest request ) {

		if( fromTrustedFrontEnd( request ) ) {
			final String adaptorAddress = header( request, "x-webobjects-remote-addr" );

			if( adaptorAddress != null ) {
				return adaptorAddress;
			}

			final String forwardedFor = header( request, "x-forwarded-for" );

			if( forwardedFor != null ) {
				final String[] chain = forwardedFor.split( "," );
				final String last = chain[chain.length - 1].strip();

				if( !last.isEmpty() ) {
					return last;
				}
			}
		}

		final InetAddress connection = request._originatingAddress();
		return connection == null ? null : connection.getHostAddress();
	}

	/**
	 * @return The host the request asked for, as sent (possibly with a port), null if none is known
	 */
	public static String host( final WORequest request ) {

		if( fromTrustedFrontEnd( request ) ) {
			for( final String name : List.of( "x-webobjects-server-name", "x-forwarded-host" ) ) {
				final String value = header( request, name );

				if( value != null ) {
					return value;
				}
			}
		}

		return header( request, "host" );
	}

	/**
	 * @return true if the request came over https: as a trusted front end reports it, otherwise false (the application's
	 *         own connections are plain http)
	 */
	public static boolean secure( final WORequest request ) {

		if( !fromTrustedFrontEnd( request ) ) {
			return false;
		}

		final String https = header( request, "https" );

		if( https != null ) {
			return https.equalsIgnoreCase( "on" );
		}

		final String proto = header( request, ERXProperties.stringForKeyWithDefault( ERXP.X_FORWARDED_PROTO_HEADER_KEY_FOR_SSL.id(), "x-forwarded-proto" ) );

		if( proto != null ) {
			return proto.equalsIgnoreCase( ERXProperties.stringForKeyWithDefault( ERXP.X_FORWARDED_PROTO_FOR_SSL.id(), "https" ) );
		}

		for( final String name : List.of( "x-webobjects-server-port", "SERVER_PORT" ) ) {
			final String port = header( request, name );

			if( port != null ) {
				return port.equals( "443" );
			}
		}

		return false;
	}

	/**
	 * @return true if the request's connection comes from a trusted front end, whose headers are believed
	 */
	public static boolean fromTrustedFrontEnd( final WORequest request ) {
		final InetAddress connection = request._originatingAddress();
		return connection != null && trusted().stream().anyMatch( network -> network.contains( connection ) );
	}

	private static String header( final WORequest request, final String name ) {
		final String value = request.headerForKey( name );
		return value == null || value.isBlank() ? null : value.strip();
	}

	private static List<Network> trusted() {
		List<Network> trusted = _trusted;

		if( trusted == null ) {
			trusted = parse( ERXProperties.stringForKeyWithDefault( TRUSTED_FRONT_ENDS_PROPERTY, DEFAULT_TRUSTED_FRONT_ENDS ) );
			_trusted = trusted;
		}

		return trusted;
	}

	/**
	 * @return The networks in a list of addresses and networks ({@code 10.1.2.3, 192.168.0.0/16})
	 * @throws IllegalArgumentException for one that isn't an address or a network, naming the property
	 */
	static List<Network> parse( final String networks ) {
		final List<Network> parsed = new ArrayList<>();

		for( final String entry : networks.split( "," ) ) {
			if( !entry.isBlank() ) {
				parsed.add( Network.parse( entry.strip() ) );
			}
		}

		return List.copyOf( parsed );
	}

	/**
	 * An address and how many of its leading bits a network shares
	 */
	record Network( byte[] address, int prefix ) {

		static Network parse( final String entry ) {
			final int slash = entry.indexOf( '/' );

			try {
				// A literal address only, so it's never looked up as a name
				if( !entry.matches( "[0-9a-fA-F.:]+(/[0-9]+)?" ) ) {
					throw new IllegalArgumentException();
				}

				final byte[] address = InetAddress.getByName( slash == -1 ? entry : entry.substring( 0, slash ) ).getAddress();
				final int prefix = slash == -1 ? address.length * 8 : Integer.parseInt( entry.substring( slash + 1 ) );

				if( prefix < 0 || prefix > address.length * 8 ) {
					throw new IllegalArgumentException();
				}

				return new Network( address, prefix );
			}
			catch( UnknownHostException | IllegalArgumentException e ) {
				throw new IllegalArgumentException( "%s has '%s', which isn't an address or a network (10.1.2.3, 192.168.0.0/16)".formatted( TRUSTED_FRONT_ENDS_PROPERTY, entry ) );
			}
		}

		boolean contains( final InetAddress candidate ) {
			byte[] bytes = candidate.getAddress();

			// An IPv4 address mapped into IPv6 (::ffff:10.0.0.1) is the IPv4 address
			if( bytes.length == 16 && address.length == 4 && isMappedIPv4( bytes ) ) {
				bytes = java.util.Arrays.copyOfRange( bytes, 12, 16 );
			}

			if( bytes.length != address.length ) {
				return false;
			}

			final int bits = bytes.length * 8;
			final BigInteger mask = prefix == 0 ? BigInteger.ZERO : BigInteger.ONE.shiftLeft( bits ).subtract( BigInteger.ONE ).shiftRight( bits - prefix ).shiftLeft( bits - prefix );
			return new BigInteger( 1, bytes ).and( mask ).equals( new BigInteger( 1, address ).and( mask ) );
		}

		private static boolean isMappedIPv4( final byte[] bytes ) {
			for( int i = 0; i < 10; i++ ) {
				if( bytes[i] != 0 ) {
					return false;
				}
			}

			return bytes[10] == (byte)0xff && bytes[11] == (byte)0xff;
		}

		@Override
		public String toString() {
			try {
				return InetAddress.getByAddress( address ).getHostAddress().toLowerCase( Locale.ROOT ) + "/" + prefix;
			}
			catch( UnknownHostException e ) {
				return "?/" + prefix;
			}
		}
	}
}
