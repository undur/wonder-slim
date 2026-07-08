package er.extensions.components;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WODynamicElement;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;

/**
 * A clean-room {@code <img>} element, written from scratch on {@link WODynamicElement} with no
 * dependence on the stock WOImage (or its ancient image-dimension probing) and no ERExtensions
 * base-class lineage. It renders a single self-closing {@code <img>} tag.
 *
 * <h2>Bindings</h2>
 * <ul>
 * <li><b>src</b> — an explicit image URL, used verbatim. Mutually exclusive with {@code filename}.</li>
 * <li><b>filename</b> — a resource name, resolved to a URL through the application's resource
 * manager (honouring the {@code framework} and the request's languages).</li>
 * <li><b>framework</b> — optional framework name for resolving {@code filename}. Omit (or bind to
 * {@code null}) for an application resource.</li>
 * </ul>
 *
 * <p>
 * Every other binding is passed straight through as a literal HTML attribute — {@code alt},
 * {@code class}, {@code id}, {@code width}, {@code height}, {@code style}, {@code loading},
 * {@code decoding}, {@code srcset}, {@code sizes}, ARIA attributes, {@code data-*}, and so on. There
 * is no fixed allow-list, so the element automatically supports current and future {@code <img>}
 * attributes without code changes.
 *
 * <h2>Deliberate non-features</h2>
 * Unlike stock WOImage, this element does <b>not</b> try to compute an image's intrinsic width and
 * height by reading its bytes. That logic only ever understood a handful of ancient raster formats,
 * produced the notorious "could not get height/width information" NSLog.err noise for anything else
 * (e.g. SVG, which is vector and has no intrinsic pixel size), and added a per-render byte read for
 * no real benefit. If you want {@code width}/{@code height} on the tag, bind them — they pass
 * through like any other attribute.
 *
 * <p>
 * Registered as {@code <wo:svg>} for now (see parsley-tag-aliases.properties); intended to become
 * the single image element for the whole framework, and eventually the shared image element for
 * ng-objects.
 *
 * <p>
 * WODynamicElements must be thread-safe: instances are shared across requests, so this class holds
 * only the (immutable, thread-safe) associations and never per-request state.
 */

public class ERXWOImage extends WODynamicElement {

	/**
	 * Bindings this element consumes itself; everything else is rendered as a passthrough attribute.
	 */
	private final WOAssociation _src;
	private final WOAssociation _filename;
	private final WOAssociation _framework;

	/**
	 * All associations except the ones we consume, rendered verbatim as HTML attributes.
	 */
	private final NSDictionary<String, WOAssociation> _passthroughAssociations;

	public ERXWOImage( String name, NSDictionary<String, WOAssociation> associations, WOElement template ) {
		super( name, associations, template );

		_src = associations.objectForKey( "src" );
		_filename = associations.objectForKey( "filename" );
		_framework = associations.objectForKey( "framework" );

		final NSMutableDictionary<String, WOAssociation> passthrough = associations.mutableClone();
		passthrough.removeObjectForKey( "src" );
		passthrough.removeObjectForKey( "filename" );
		passthrough.removeObjectForKey( "framework" );
		_passthroughAssociations = passthrough.immutableClone();
	}

	@Override
	public void appendToResponse( WOResponse response, WOContext context ) {

		final WOComponent component = context.component();
		final String url = imageURL( component, context );

		response.appendContentString( "<img" );

		if( url != null ) {
			appendAttribute( response, "src", url );
		}

		// Everything the caller bound that we don't consume ourselves becomes a literal attribute.
		for( final String attributeName : _passthroughAssociations.allKeys() ) {
			final Object value = _passthroughAssociations.objectForKey( attributeName ).valueInComponent( component );

			if( value != null ) {
				appendAttribute( response, attributeName, value.toString() );
			}
		}

		response.appendContentString( " />" );
	}

	/**
	 * Resolves the image URL from the bindings: an explicit {@code src} wins; otherwise a
	 * {@code filename} is resolved through the resource manager. Returns {@code null} if neither is
	 * bound (or both resolve to null), in which case no {@code src} attribute is emitted.
	 */
	private String imageURL( WOComponent component, WOContext context ) {

		if( _src != null ) {
			final Object value = _src.valueInComponent( component );
			return value == null ? null : value.toString();
		}

		if( _filename != null ) {
			final Object filenameValue = _filename.valueInComponent( component );

			if( filenameValue == null ) {
				return null;
			}

			final String frameworkName = (_framework == null) ? null : (String)_framework.valueInComponent( component );

			return WOApplication.application().resourceManager().urlForResourceNamed( filenameValue.toString(), frameworkName, languages( context ), context.request() );
		}

		return null;
	}

	/**
	 * Appends a single {@code name="value"} attribute, HTML-escaping the value. Isolates our one
	 * unavoidable use of WO's internal tag-attribute append.
	 */
	private static void appendAttribute( WOResponse response, String name, String value ) {
		response._appendTagAttributeAndValue( name, value, true );
	}

	/**
	 * The request's preferred languages, for localized resource resolution. Isolates the one
	 * internal WOContext call.
	 */
	private static com.webobjects.foundation.NSArray<String> languages( WOContext context ) {
		return context._languages();
	}
}
