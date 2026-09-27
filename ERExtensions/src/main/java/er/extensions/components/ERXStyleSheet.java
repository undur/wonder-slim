package er.extensions.components;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WODynamicElementCreationException;
import com.webobjects.appserver._private.WODynamicGroup;
import com.webobjects.appserver._private.WOHTMLDynamicElement;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ERXResponseRewriter;
import er.extensions.appserver.ajax.ERXAjaxApplication;
import er.extensions.resources.ERXResourceManagerBase;

/**
 * Adds a stylesheet to the page: a {@code <link>} tag for a stylesheet resource ({@code filename}, optionally in a
 * {@code framework}) or a URL ({@code href}). The tag goes before the page's {@code </head>}, or where the element is,
 * and a stylesheet already added to the page isn't added again.
 *
 * @binding filename name of the stylesheet resource
 * @binding framework name of the framework containing the stylesheet resource
 * @binding href URL of the stylesheet, instead of filename/framework
 * @binding media the stylesheet's media query
 * @binding inline when <code>true</code>, the tag is rendered where the element is; when <code>false</code>, before
 *          the page's head end tag. When unset, inline for Ajax requests and in the head otherwise.
 *
 *          Any other binding is rendered as an attribute of the tag, such as {@code integrity} and {@code crossorigin}.
 */

public class ERXStyleSheet extends WOHTMLDynamicElement {

	private final WOAssociation _filename;
	private final WOAssociation _framework;
	private final WOAssociation _href;
	private final WOAssociation _media;
	private final WOAssociation _inline;

	public ERXStyleSheet( final String name, final NSDictionary<String, WOAssociation> associations, final WOElement template ) {
		super( "link", associations, null );

		if( hasContent( template ) ) {
			throw new WODynamicElementCreationException( "<" + getClass().getName() + "> no longer renders its content as a stylesheet. Put the CSS in a stylesheet file (filename/framework or href) or in a <style> tag." );
		}

		refuse( "key", "Rendering the element's content as a cached stylesheet has been removed. Put the CSS in a stylesheet file (filename/framework or href) or in a <style> tag." );
		refuse( "styleSheetUrl", "Use href." );
		refuse( "styleSheetName", "Use filename." );
		refuse( "styleSheetFrameworkName", "Use framework." );

		_filename = _associations.removeObjectForKey( "filename" );
		_framework = _associations.removeObjectForKey( "framework" );
		_href = _associations.removeObjectForKey( "href" );
		_media = _associations.removeObjectForKey( "media" );
		_inline = _associations.removeObjectForKey( "inline" );

		if( (_filename == null) == (_href == null) ) {
			throw new WODynamicElementCreationException( "<" + getClass().getName() + "> Exactly one of 'filename' or 'href' must be bound." );
		}
	}

	/**
	 * @return true if the element has content. A template parser may pass an empty group for an element without any
	 *         (Parsley does, where WO's own parser passes null).
	 */
	private static boolean hasContent( final WOElement template ) {
		if( template == null ) {
			return false;
		}

		return !(template instanceof WODynamicGroup group) || group.hasChildrenElements();
	}

	/**
	 * Throws if the named binding, which no longer exists, is bound
	 */
	private void refuse( final String bindingName, final String explanation ) {
		if( _associations.objectForKey( bindingName ) != null ) {
			throw new WODynamicElementCreationException( "<" + getClass().getName() + "> The '" + bindingName + "' binding has been removed. " + explanation );
		}
	}

	@Override
	public void appendToResponse( final WOResponse response, final WOContext context ) {
		final WOComponent component = context.component();
		final String filename = _filename != null ? (String)_filename.valueInComponent( component ) : null;
		final String framework = _framework != null ? (String)_framework.valueInComponent( component ) : null;

		if( filename != null && ERXResponseRewriter.isResourceAddedToHead( context, framework, filename ) ) {
			return;
		}

		final String href = filename != null ? urlForResource( filename, framework, context ) : (String)_href.valueInComponent( component );

		if( href == null ) {
			return;
		}

		final boolean inline = _inline != null ? _inline.booleanValueInComponent( component ) : ERXAjaxApplication.isAjaxRequest( context.request() );
		final WOResponse tagResponse = inline ? response : new WOResponse();

		tagResponse._appendContentAsciiString( "<link" );
		tagResponse._appendTagAttributeAndValue( "rel", filename != null && filename.toLowerCase().endsWith( ".less" ) ? "stylesheet/less" : "stylesheet", false );
		tagResponse._appendTagAttributeAndValue( "type", "text/css", false );
		tagResponse._appendTagAttributeAndValue( "href", href, false );
		tagResponse._appendTagAttributeAndValue( "media", _media != null ? (String)_media.valueInComponent( component ) : null, false );
		appendAttributesToResponse( tagResponse, context );
		tagResponse._appendContentAsciiString( "/>" );

		final boolean added = inline || ERXResponseRewriter.insertInResponseBeforeHead( response, context, tagResponse.contentString(), ERXResponseRewriter.TagMissingBehavior.Inline );

		if( added ) {
			if( filename != null ) {
				ERXResponseRewriter.resourceAddedToHead( context, framework, filename );
			}
			else {
				ERXResponseRewriter.resourceAddedToHead( context, null, href );
			}
		}
	}

	/**
	 * @return The URL of the stylesheet resource, complete if complete resource URLs are being generated
	 */
	private static String urlForResource( final String filename, final String framework, final WOContext context ) {
		final String url = context._urlForResourceNamed( filename, framework, true );

		if( url != null && ERXResourceManagerBase._shouldGenerateCompleteResourceURL( context ) ) {
			return ERXResourceManagerBase._completeURLForResource( url, null, context );
		}

		return url;
	}
}
