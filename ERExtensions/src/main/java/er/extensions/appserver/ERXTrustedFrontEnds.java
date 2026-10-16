package er.extensions.appserver;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import er.extensions.ERXP;
import er.extensions.foundation.ERXProperties;

/**
 * The front ends whose forwarding headers a request is believed on, as configured with
 * {@link ERXP#TRUSTED_FRONT_ENDS}. See {@link ERXRequest#isFromTrustedFrontEnd(com.webobjects.appserver.WORequest)}.
 */
final class ERXTrustedFrontEnds {

	static final String DEFAULT = "loopback, private";

	/**
	 * The last configuration read, kept until the property changes
	 */
	private static volatile ERXTrustedFrontEnds _current;

	private final String _configuration;
	private final boolean _all;
	private final boolean _loopback;
	private final boolean _private;
	private final List<Network> _networks;

	/**
	 * An address and the number of leading bits a match shares with it
	 */
	private record Network( byte[] address, int prefixLength ) {

		boolean contains( final InetAddress candidate ) {
			final byte[] bytes = candidate.getAddress();

			if( bytes.length != address.length ) {
				return false;
			}

			final int fullBytes = prefixLength / 8;

			for( int i = 0; i < fullBytes; i++ ) {
				if( bytes[i] != address[i] ) {
					return false;
				}
			}

			final int remainingBits = prefixLength % 8;

			if( remainingBits == 0 ) {
				return true;
			}

			final int mask = 0xff << (8 - remainingBits);
			return (bytes[fullBytes] & mask) == (address[fullBytes] & mask);
		}
	}

	private ERXTrustedFrontEnds( final String configuration ) {
		_configuration = configuration;

		boolean all = false;
		boolean loopback = false;
		boolean privateNetworks = false;
		final List<Network> networks = new ArrayList<>();

		for( final String entry : configuration.split( "[,\\s]+" ) ) {
			switch( entry ) {
				case "" -> {}
				case "all" -> all = true;
				case "loopback" -> loopback = true;
				case "private" -> privateNetworks = true;
				default -> networks.add( network( entry ) );
			}
		}

		_all = all;
		_loopback = loopback;
		_private = privateNetworks;
		_networks = List.copyOf( networks );
	}

	/**
	 * @return The front ends as currently configured
	 */
	static ERXTrustedFrontEnds current() {
		final String configuration = ERXProperties.stringForKeyWithDefault( ERXP.TRUSTED_FRONT_ENDS.id(), DEFAULT );
		ERXTrustedFrontEnds current = _current;

		if( current == null || !current._configuration.equals( configuration ) ) {
			current = parse( configuration );
			_current = current;
		}

		return current;
	}

	/**
	 * @throws IllegalArgumentException For an entry that isn't an address, a network or one of the keywords
	 */
	static ERXTrustedFrontEnds parse( final String configuration ) {
		return new ERXTrustedFrontEnds( configuration );
	}

	private static Network network( final String entry ) {
		final int slash = entry.indexOf( '/' );
		final InetAddress address = literalAddress( slash == -1 ? entry : entry.substring( 0, slash ) );

		if( address == null ) {
			throw new IllegalArgumentException( "'%s' in %s is not an address, a network (address/bits), 'loopback', 'private' or 'all'".formatted( entry, ERXP.TRUSTED_FRONT_ENDS.id() ) );
		}

		final int maximum = address.getAddress().length * 8;
		int prefixLength = maximum;

		if( slash != -1 ) {
			try {
				prefixLength = Integer.parseInt( entry.substring( slash + 1 ) );
			}
			catch( final NumberFormatException e ) {
				prefixLength = -1;
			}

			if( prefixLength < 0 || prefixLength > maximum ) {
				throw new IllegalArgumentException( "'%s' in %s: the number of bits must be between 0 and %s".formatted( entry, ERXP.TRUSTED_FRONT_ENDS.id(), maximum ) );
			}
		}

		return new Network( address.getAddress(), prefixLength );
	}

	/**
	 * @return The address written in the string, null if it isn't one (a host name is never looked up)
	 */
	static InetAddress literalAddress( final String string ) {
		try {
			return InetAddress.ofLiteral( string.strip() );
		}
		catch( final IllegalArgumentException e ) {
			return null;
		}
	}

	/**
	 * @return true if requests from the address are from a trusted front end
	 */
	boolean contains( final InetAddress address ) {
		if( _all ) {
			return true;
		}

		if( _loopback && address.isLoopbackAddress() ) {
			return true;
		}

		if( _private && isPrivate( address ) ) {
			return true;
		}

		for( final Network network : _networks ) {
			if( network.contains( address ) ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * @return true for an address on a private network: 10/8, 172.16/12 and 192.168/16, and IPv6 unique local addresses (fc00::/7)
	 */
	private static boolean isPrivate( final InetAddress address ) {
		final byte[] bytes = address.getAddress();

		if( bytes.length == 16 ) {
			return (bytes[0] & 0xfe) == 0xfc;
		}

		return address.isSiteLocalAddress();
	}
}
