package ajaxplayground;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.webobjects.appserver.WOResponse;

import ajaxplayground.apiext.ApiextElement;
import ajaxplayground.components.ElementReference;
import ajaxplayground.components.ElementReference.Source;

/**
 * The element reference as JSON, for other sites to render in their own style (whoacommunity.com does).
 * {@code /element-reference/sources.json} lists the sources; {@code <source path>.json} carries that source's
 * elements: the same parsed {@code .apiext} model the HTML page renders, with the Markdown docs already
 * rendered to HTML snippets. There is no JSON library on the classpath, so the writing is done by hand.
 */
public final class ElementReferenceJSON {

	private ElementReferenceJSON() {}

	/**
	 * @return The list of sources: id, page path, JSON path, title and lead text
	 */
	public static WOResponse index() {
		final StringBuilder sb = new StringBuilder( "{\"sources\":[" );
		boolean first = true;

		for( final Source source : Source.values() ) {
			if( !first ) {
				sb.append( ',' );
			}
			first = false;
			sb.append( '{' );
			field( sb, "id", source.name() ).append( ',' );
			field( sb, "path", source.path() ).append( ',' );
			field( sb, "json", source.path() + ".json" ).append( ',' );
			field( sb, "title", source.title() ).append( ',' );
			field( sb, "leadHtml", source.leadHtml() );
			sb.append( '}' );
		}

		sb.append( "]}" );
		return response( sb.toString() );
	}

	/**
	 * @return One source's elements, in reference order
	 */
	public static WOResponse source( final Source source ) {
		final StringBuilder sb = new StringBuilder( "{" );
		field( sb, "id", source.name() ).append( ',' );
		field( sb, "title", source.title() ).append( ',' );
		field( sb, "leadHtml", source.leadHtml() ).append( ',' );
		sb.append( "\"elements\":[" );
		boolean first = true;

		for( final ApiextElement el : ElementReference.elementsFor( source ) ) {
			if( !first ) {
				sb.append( ',' );
			}
			first = false;
			element( sb, source, el );
		}

		sb.append( "]}" );
		return response( sb.toString() );
	}

	private static void element( final StringBuilder sb, final Source source, final ApiextElement el ) {
		sb.append( '{' );
		field( sb, "className", el.className() ).append( ',' );
		field( sb, "docHtml", el.docHtml() ).append( ',' );
		field( sb, "deprecated", el.isDeprecated() ).append( ',' );
		field( sb, "deprecatedHtml", el.isDeprecated() ? el.deprecatedHtml() : null ).append( ',' );
		field( sb, "expectsContent", el.expectsContent() ).append( ',' );
		field( sb, "passthrough", el.passthrough() ).append( ',' );
		field( sb, "forbidsUnknownAttributes", el.forbidden() ).append( ',' );
		sb.append( "\"tags\":" );
		strings( sb, ElementReference.tagsFor( source, el ) );
		sb.append( ",\"bindings\":[" );
		boolean first = true;

		for( final ApiextElement.Binding b : el.bindings ) {
			if( !first ) {
				sb.append( ',' );
			}
			first = false;
			sb.append( '{' );
			field( sb, "name", b.name ).append( ',' );
			field( sb, "required", b.required ).append( ',' );
			field( sb, "docHtml", b.docHtml() ).append( ',' );
			field( sb, "defaultValue", b.defaultValue ).append( ',' );
			field( sb, "deprecated", b.isDeprecated() ).append( ',' );
			field( sb, "deprecatedHtml", b.isDeprecated() ? b.deprecatedHtml() : null ).append( ',' );
			field( sb, "direction", b.pulls() && b.pushes() ? "both" : b.pulls() ? "pull" : b.pushes() ? "push" : null ).append( ',' );
			field( sb, "pullType", b.pulls() ? b.displayPullType() : null ).append( ',' );
			field( sb, "pushType", b.pushes() ? b.displayPushType() : null ).append( ',' );
			sb.append( "\"pullTypes\":" );
			types( sb, b.pullTypes );
			sb.append( ",\"pushTypes\":" );
			types( sb, b.pushTypes );
			sb.append( '}' );
		}

		sb.append( "],\"constraints\":[" );
		first = true;

		for( final ApiextElement.Constraint c : el.constraints ) {
			if( !first ) {
				sb.append( ',' );
			}
			first = false;
			sb.append( '{' );
			field( sb, "kind", c.kind() ).append( ',' );
			field( sb, "message", c.message() );
			sb.append( '}' );
		}

		sb.append( "]}" );
	}

	private static void types( final StringBuilder sb, final List<ApiextElement.Type> types ) {
		sb.append( '[' );
		boolean first = true;

		for( final ApiextElement.Type t : types ) {
			if( !first ) {
				sb.append( ',' );
			}
			first = false;
			sb.append( '{' );
			field( sb, "fqn", t.fqn ).append( ',' );
			field( sb, "interpretation", t.interpretation ).append( ',' );
			field( sb, "display", t.display() );
			sb.append( '}' );
		}

		sb.append( ']' );
	}

	private static void strings( final StringBuilder sb, final List<String> values ) {
		sb.append( '[' );
		boolean first = true;

		for( final String v : values ) {
			if( !first ) {
				sb.append( ',' );
			}
			first = false;
			string( sb, v );
		}

		sb.append( ']' );
	}

	private static StringBuilder field( final StringBuilder sb, final String name, final String value ) {
		string( sb, name ).append( ':' );
		return string( sb, value );
	}

	private static StringBuilder field( final StringBuilder sb, final String name, final boolean value ) {
		return string( sb, name ).append( ':' ).append( value );
	}

	/**
	 * Appends a JSON string literal (or null), escaped per RFC 8259
	 */
	private static StringBuilder string( final StringBuilder sb, final String value ) {
		if( value == null ) {
			return sb.append( "null" );
		}

		sb.append( '"' );

		for( int i = 0; i < value.length(); i++ ) {
			final char c = value.charAt( i );

			switch( c ) {
				case '"' -> sb.append( "\\\"" );
				case '\\' -> sb.append( "\\\\" );
				case '\n' -> sb.append( "\\n" );
				case '\r' -> sb.append( "\\r" );
				case '\t' -> sb.append( "\\t" );
				default -> {
					if( c < 0x20 ) {
						sb.append( String.format( "\\u%04x", (int)c ) );
					}
					else {
						sb.append( c );
					}
				}
			}
		}

		return sb.append( '"' );
	}

	private static WOResponse response( final String json ) {
		final WOResponse response = new WOResponse();
		response.setHeader( "application/json; charset=utf-8", "content-type" );
		response.setHeader( "public, max-age=300", "cache-control" );
		response.setContent( json.getBytes( StandardCharsets.UTF_8 ) );
		return response;
	}
}
