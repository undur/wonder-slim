package er.routing;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;

import er.extensions.components.replacements.ERXWOForm;

/**
 * EXPERIMENTAL (route-links branch). A form posting to a typed route, as {@code <wo:routeForm>}, see docs/ROUTING.md.
 *
 * <pre>
 * &lt;wo:routeForm route="$routes.createBook"&gt;
 *     &lt;input name="title"&gt; &lt;input name="author"&gt;
 * &lt;/wo:routeForm&gt;
 * </pre>
 *
 * {@code route} binds a {@link Route}, and each {@code :name} binding one of its parameters, as on {@code <wo:route>}.
 * Host parameters it doesn't bind are the current request's, and the record's other components are the form's fields,
 * filled when it's posted. The URL comes from the route only, so {@code href}, {@code action} and the direct action
 * bindings aren't accepted. The method is {@code post} unless bound.
 *
 * A {@code get} form's query parameters ({@code :sort="$sort"}) are hidden fields, since a browser replaces the query of
 * a get form's action with the form's fields.
 */

public class ERXRouteForm extends ERXWOForm {

	private static final List<String> URL_KEYS = List.of( "href", "action", "directActionName", "actionClass" );

	/**
	 * The form's method, post unless bound
	 */
	private final WOAssociation _method;

	public ERXRouteForm( final String name, final NSDictionary associations, final WOElement template ) {
		super( name, RouteBindings.withRouteURL( associations, "<wo:routeForm>", "<wo:form>", URL_KEYS ), template );

		// Read now: the associations left for attributes are let go of after the element is built
		_method = _associations.objectForKey( "method" );
	}

	@Override
	public void appendChildrenToResponse( final WOResponse response, final WOContext context ) {
		super.appendChildrenToResponse( response, context );

		if( _method == null || !"get".equalsIgnoreCase( String.valueOf( _method.valueInComponent( context.component() ) ) ) ) {
			return;
		}

		// The URL is an attribute's value, its & escaped
		final String url = String.valueOf( _href.valueInComponent( context.component() ) ).replace( "&amp;", "&" );
		final int q = url.indexOf( '?' );

		if( q == -1 ) {
			return;
		}

		for( final String pair : url.substring( q + 1 ).split( "&" ) ) {
			final int equals = pair.indexOf( '=' );
			final String name = URLDecoder.decode( equals == -1 ? pair : pair.substring( 0, equals ), StandardCharsets.UTF_8 );
			final String value = equals == -1 ? "" : URLDecoder.decode( pair.substring( equals + 1 ), StandardCharsets.UTF_8 );
			response.appendContentString( "<input type=\"hidden\" name=\"" );
			response.appendContentHTMLAttributeValue( name );
			response.appendContentString( "\" value=\"" );
			response.appendContentHTMLAttributeValue( value );
			response.appendContentString( "\">" );
		}
	}
}
