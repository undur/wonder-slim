package er.extensions.components;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WODynamicElementCreationException;
import com.webobjects.appserver._private.WODynamicGroup;
import com.webobjects.appserver._private.WOHTMLDynamicElement;
import com.webobjects.appserver._private.WOStaticURLUtilities;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ERXResponseRewriter;
import er.extensions.resources.ERXResourceManagerBase;

/**
 * Adds a script to the page: a {@code <script>} tag for a script resource ({@code filename}, optionally in a
 * {@code framework}) or a URL (a {@code filename} that's a complete URL), rendered where the element is.
 *
 * @binding filename name of the script resource, or the script's complete URL
 * @binding framework name of the framework containing the script resource
 *
 *          Any other binding is rendered as an attribute of the tag, such as {@code defer}, {@code async},
 *          {@code integrity} and {@code crossorigin}. A {@code type} binding (such as {@code module}) replaces the
 *          default {@code text/javascript}.
 *
 *          Deprecated binding names, still accepted so older templates keep working, each used only when its
 *          replacement isn't bound: {@code scriptSource} (use {@code filename}) and {@code scriptFramework} (use
 *          {@code framework}).
 */

public class ERXJavaScript extends WOHTMLDynamicElement {

	private static final Logger log = LoggerFactory.getLogger( ERXJavaScript.class );

	private final WOAssociation _filename;
	private final WOAssociation _framework;
	private final boolean _typeBound;

	public ERXJavaScript( final String name, final NSDictionary<String, WOAssociation> associations, final WOElement template ) {
		super( "script", associations, null );

		if( hasContent( template ) ) {
			throw new WODynamicElementCreationException( "<" + getClass().getName() + "> no longer renders its content as a script. Put the script in a file (filename/framework) or in a <script> tag." );
		}

		refuse( "scriptKey", "Rendering the element's content as a script cached in the session has been removed. Put the script in a file (filename/framework) or in a <script> tag." );
		refuse( "scriptString", "Put the script in a <script> tag." );
		refuse( "scriptFile", "Reference the file with filename/framework, or put the script in a <script> tag." );
		refuse( "hideInComment", "Script content is no longer rendered." );

		// Each binding with its deprecated older name, used only when the current one isn't bound
		_filename = removeBinding( "filename", "scriptSource" );
		_framework = removeBinding( "framework", "scriptFramework" );

		// Obsolete in HTML since 4.01, and never rendered by this element
		_associations.removeObjectForKey( "language" );

		if( _filename == null ) {
			throw new WODynamicElementCreationException( "<" + getClass().getName() + "> 'filename' must be bound." );
		}

		_typeBound = _associations.objectForKey( "type" ) != null;
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
	 * @return The association for the binding, or for its deprecated older name if the binding isn't bound. Both are removed, so neither is rendered as an attribute.
	 */
	private WOAssociation removeBinding( final String name, final String deprecatedName ) {
		final WOAssociation association = _associations.removeObjectForKey( name );
		final WOAssociation deprecatedAssociation = _associations.removeObjectForKey( deprecatedName );
		return association != null ? association : deprecatedAssociation;
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
		_appendOpenTagToResponse( response, context );
		_appendCloseTagToResponse( response, context );
	}

	@Override
	public void appendAttributesToResponse( final WOResponse response, final WOContext context ) {
		final WOComponent component = context.component();

		if( !_typeBound ) {
			response._appendContentAsciiString( " type=\"text/javascript\"" );
		}

		final String filename = (String)_filename.valueInComponent( component );
		String framework = null;
		String src = null;

		if( filename != null ) {
			if( !WOStaticURLUtilities.isRelativeURL( filename ) ) {
				src = filename;
			}
			else if( WOStaticURLUtilities.isFragmentURL( filename ) ) {
				log.warn( "relative fragment URL {}", filename );
			}
			else {
				framework = _framework != null ? (String)_framework.valueInComponent( component ) : null;
				src = context._urlForResourceNamed( filename, framework, true );

				if( src == null ) {
					src = component.baseURL() + "/" + filename;
				}
				else if( ERXResourceManagerBase._shouldGenerateCompleteResourceURL( context ) ) {
					src = ERXResourceManagerBase._completeURLForResource( src, null, context );
				}
			}
		}

		if( src != null ) {
			response._appendContentAsciiString( " src=\"" );
			response.appendContentString( src );
			response.appendContentCharacter( '"' );
		}

		super.appendAttributesToResponse( response, context );

		if( src != null && WOStaticURLUtilities.isRelativeURL( filename ) ) {
			ERXResponseRewriter.resourceAddedToHead( context, framework, filename );
		}
	}

	@Override
	public String toString() {
		return "<" + getClass().getName() + " filename=" + _filename + " framework=" + _framework + ">";
	}
}
