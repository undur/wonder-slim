package ajaxplayground.components.scenario;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver.sse.SSEHub;
import com.webobjects.appserver.sse.SSEStream;

/**
 * Datastar's SSE protocol (v1) on top of {@link SSEStream}: patch elements, patch signals. Datastar drives its whole UI
 * from these two events, so a page built on it is a strict test of the adaptor's streaming responses - every button
 * click is an SSE stream, and the page's live state is a long-lived one.
 *
 * Wire format, per the Datastar reference:
 *
 * <pre>
 * event: datastar-patch-elements
 * data: selector #foo
 * data: mode inner
 * data: elements &lt;div&gt;
 * data: elements   Hello
 * data: elements &lt;/div&gt;
 *
 * event: datastar-patch-signals
 * data: signals {"count": 3}
 * </pre>
 *
 * Demo-grade: lives in the playground for now. If it earns its keep it belongs in the push module.
 */
public final class DatastarSSE {

	public static final String PATCH_ELEMENTS = "datastar-patch-elements";
	public static final String PATCH_SIGNALS = "datastar-patch-signals";

	private final SSEStream _stream;

	public DatastarSSE() {
		_stream = new SSEStream();
	}

	public DatastarSSE( final Duration keepAliveInterval ) {
		_stream = new SSEStream( keepAliveInterval );
	}

	public DatastarSSE( final SSEStream stream ) {
		_stream = stream;
	}

	public SSEStream stream() {
		return _stream;
	}

	public WOResponse response() {
		return _stream.response();
	}

	public boolean isOpen() {
		return _stream.isOpen();
	}

	public void close() {
		_stream.close();
	}

	/**
	 * Patch elements by their ids (Datastar's default: mode outer, no selector - each top-level element in the HTML
	 * replaces the element with the same id).
	 */
	public void patchElements( final String html ) {
		patchElements( null, null, html );
	}

	/**
	 * @param selector CSS selector of the target, or null to match by id
	 * @param mode outer (default), inner, replace, prepend, append, before, after, remove - or null for the default
	 */
	public void patchElements( final String selector, final String mode, final String html ) {
		_stream.send( PATCH_ELEMENTS, elementsData( selector, mode, html ) );
	}

	/**
	 * Remove the elements matched by the selector.
	 */
	public void removeElements( final String selector ) {
		_stream.send( PATCH_ELEMENTS, "selector " + selector + "\nmode remove" );
	}

	/**
	 * Merge signals into the page's signals. The argument is a JSON object.
	 */
	public void patchSignals( final String json ) {
		_stream.send( PATCH_SIGNALS, signalsData( json ) );
	}

	// Broadcast variants, for every stream in a hub

	public static void patchElements( final SSEHub hub, final String selector, final String mode, final String html ) {
		hub.broadcast( PATCH_ELEMENTS, elementsData( selector, mode, html ) );
	}

	public static void patchSignals( final SSEHub hub, final String json ) {
		hub.broadcast( PATCH_SIGNALS, signalsData( json ) );
	}

	/**
	 * @return The data lines of a patch-elements event (without the "data: " prefixes, which SSEStream adds per line)
	 */
	public static String elementsData( final String selector, final String mode, final String html ) {
		final StringBuilder data = new StringBuilder();

		if( selector != null ) {
			data.append( "selector " ).append( selector ).append( '\n' );
		}

		if( mode != null ) {
			data.append( "mode " ).append( mode ).append( '\n' );
		}

		for( final String line : html.split( "\r?\n" ) ) {
			data.append( "elements " ).append( line ).append( '\n' );
		}

		return data.toString().stripTrailing();
	}

	public static String signalsData( final String json ) {
		return "signals " + json;
	}

	/**
	 * The signals Datastar sent with the request: as the {@code datastar} query parameter on GET, as the JSON body
	 * otherwise. Returns a flat name → value map of the top-level string, number and boolean members, which is all this
	 * demo needs (no JSON library on the classpath; nested objects and arrays are skipped).
	 */
	public static Map<String, String> signals( final WORequest request ) {
		String json = request.stringFormValueForKey( "datastar" );

		if( json == null && request.content() != null && request.content().length() > 0 ) {
			json = request.contentString();
		}

		return parseFlatJSON( json );
	}

	private static final Pattern MEMBER = Pattern.compile( "\"([^\"]+)\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|-?\\d+(?:\\.\\d+)?|true|false|null)" );

	static Map<String, String> parseFlatJSON( final String json ) {
		final Map<String, String> map = new LinkedHashMap<>();

		if( json == null ) {
			return map;
		}

		final Matcher m = MEMBER.matcher( json );

		while( m.find() ) {
			final String value = m.group( 3 ) != null ? m.group( 3 ).replace( "\\\"", "\"" ).replace( "\\n", "\n" ).replace( "\\\\", "\\" ) : m.group( 2 );
			map.put( m.group( 1 ), "null".equals( value ) ? null : value );
		}

		return map;
	}

	/**
	 * Escape text for inclusion in HTML we send as elements.
	 */
	public static String html( final String text ) {
		return text == null ? "" : text.replace( "&", "&amp;" ).replace( "<", "&lt;" ).replace( ">", "&gt;" ).replace( "\"", "&quot;" );
	}

	/**
	 * Quote a string as a JSON string literal.
	 */
	public static String json( final String text ) {
		return text == null ? "null" : "\"" + text.replace( "\\", "\\\\" ).replace( "\"", "\\\"" ).replace( "\n", "\\n" ) + "\"";
	}
}
