package er.extensions.appserver;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.webobjects.appserver.WORequest;

/**
 * What a request's {@code User-Agent} header says about the client, as far as it can still be trusted: the browser
 * family and its major version, the operating system, whether it's a mobile device, and whether it's a bot.
 *
 * Browsers freeze part of the user-agent string: operating system versions and minor browser versions no longer mean
 * anything, so they aren't offered. The operating system's name and the browser's major version are still accurate.
 * Anything finer belongs in client-side feature detection. Bot detection matches the header against short markers
 * ({@link #botMarkers()}), which an application can extend ({@link #addBotMarkers(String...)}).
 *
 * @param header The {@code User-Agent} header as received, null when the request carried none
 */
public record ERXUserAgent( String header, Family family, Integer majorVersion, Platform platform, boolean isMobile, boolean isBot ) {

	public enum Family {
		CHROME, SAFARI, FIREFOX, EDGE, OPERA, OTHER
	}

	public enum Platform {
		MACOS, WINDOWS, IOS, ANDROID, LINUX, OTHER
	}

	/**
	 * Lowercase substrings that mark a user agent as a bot; a marker starting with {@code ^} only matches at the start of
	 * the header. Generic words first, then crawlers and fetchers whose names don't contain them.
	 */
	private static final CopyOnWriteArrayList<String> BOT_MARKERS = new CopyOnWriteArrayList<>( List.of(
			"bot",
			"crawl",
			"spider",
			"slurp",
			"facebookexternalhit",
			"ia_archiver",
			"bytespider",
			"openai",
			"anthropic",
			"perplexity",
			"semrush",
			"barkrowler",
			"nutch",
			"velen",
			"landsbokasafn",
			"^whatsapp/", // the link previewer; WhatsApp's in-app browser (a person) also names WhatsApp, further in
			"headless",
			"scrapy",
			"python-requests",
			"python-urllib",
			"python-httpx",
			"aiohttp",
			"go-http-client",
			"java-http-client",
			"httpclient",
			"okhttp",
			"axios",
			"node-fetch",
			"curl/",
			"wget/" ) );

	/**
	 * @return The client the request's {@code User-Agent} header describes, see {@link #parse(String)}
	 */
	public static ERXUserAgent of( final WORequest request ) {
		return parse( request.headerForKey( "user-agent" ) );
	}

	/**
	 * @return The client the header describes. Never null: a missing header gives family {@link Family#OTHER}, counted as a bot.
	 */
	public static ERXUserAgent parse( final String header ) {

		if( header == null || header.isBlank() || header.trim().length() < 2 ) {
			return new ERXUserAgent( header, Family.OTHER, null, Platform.OTHER, false, true );
		}

		final String h = header.toLowerCase( Locale.ROOT );
		final Family family = family( h );
		return new ERXUserAgent( header, family, majorVersion( h, family ), platform( h ), isMobile( h ), isBot( h ) );
	}

	/**
	 * Order matters: Edge and Opera also announce Chrome, and Chrome also announces Safari.
	 */
	private static Family family( final String h ) {

		if( h.contains( "edg/" ) || h.contains( "edga/" ) || h.contains( "edgios/" ) ) {
			return Family.EDGE;
		}

		if( h.contains( "opr/" ) || h.contains( "opera" ) ) {
			return Family.OPERA;
		}

		if( h.contains( "firefox/" ) || h.contains( "fxios/" ) ) {
			return Family.FIREFOX;
		}

		if( h.contains( "chrome/" ) || h.contains( "crios/" ) || h.contains( "chromium/" ) ) {
			return Family.CHROME;
		}

		if( h.contains( "safari/" ) || h.contains( "applewebkit/" ) ) {
			return Family.SAFARI;
		}

		return Family.OTHER;
	}

	/**
	 * The version tokens each family puts its version in, in the order to try
	 */
	private static final Map<Family, List<String>> VERSION_TOKENS = Map.of(
			Family.EDGE, List.of( "edg/", "edga/", "edgios/" ),
			Family.OPERA, List.of( "opr/", "opera/" ),
			Family.FIREFOX, List.of( "firefox/", "fxios/" ),
			Family.CHROME, List.of( "chrome/", "crios/", "chromium/" ),
			Family.SAFARI, List.of( "version/" ) );

	/**
	 * @return The major version following the family's version token, null when there is none
	 */
	private static Integer majorVersion( final String h, final Family family ) {
		for( final String token : VERSION_TOKENS.getOrDefault( family, List.of() ) ) {
			final int start = h.indexOf( token );

			if( start >= 0 ) {
				int end = start + token.length();

				while( end < h.length() && Character.isDigit( h.charAt( end ) ) ) {
					end++;
				}

				if( end > start + token.length() && end - start - token.length() < 6 ) {
					return Integer.valueOf( h.substring( start + token.length(), end ) );
				}
			}
		}

		return null;
	}

	/**
	 * Order matters: Android announces Linux, and iPhones and iPads announce "like Mac OS X".
	 */
	private static Platform platform( final String h ) {

		if( h.contains( "iphone" ) || h.contains( "ipad" ) || h.contains( "ipod" ) ) {
			return Platform.IOS;
		}

		if( h.contains( "android" ) ) {
			return Platform.ANDROID;
		}

		if( h.contains( "windows" ) ) {
			return Platform.WINDOWS;
		}

		if( h.contains( "macintosh" ) || h.contains( "mac os x" ) ) {
			return Platform.MACOS;
		}

		if( h.contains( "linux" ) || h.contains( "x11" ) || h.contains( "cros" ) ) {
			return Platform.LINUX;
		}

		return Platform.OTHER;
	}

	private static boolean isMobile( final String h ) {
		return h.contains( "mobi" ) || h.contains( "android" ) || h.contains( "iphone" ) || h.contains( "ipad" );
	}

	private static boolean isBot( final String h ) {
		for( final String marker : BOT_MARKERS ) {
			if( marker.startsWith( "^" ) ? h.startsWith( marker.substring( 1 ) ) : h.contains( marker ) ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * @return The markers that identify bots, lowercase
	 */
	public static List<String> botMarkers() {
		return List.copyOf( BOT_MARKERS );
	}

	/**
	 * Adds markers (matched case-insensitively as substrings, or at the start of the header when they begin with {@code ^})
	 * that identify bots, for crawlers the built-in list misses.
	 */
	public static void addBotMarkers( final String... markers ) {
		for( final String marker : markers ) {
			if( marker != null && !marker.isBlank() ) {
				BOT_MARKERS.addIfAbsent( marker.toLowerCase( Locale.ROOT ) );
			}
		}
	}

	public boolean isChrome() {
		return family == Family.CHROME;
	}

	public boolean isSafari() {
		return family == Family.SAFARI;
	}

	public boolean isFirefox() {
		return family == Family.FIREFOX;
	}

	public boolean isEdge() {
		return family == Family.EDGE;
	}
}
