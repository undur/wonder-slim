package er.extensions.components.additions;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSDictionary;

import er.extensions.components.replacements.ERXWOForm;
import er.extensions.routing.Route;

/**
 * A form posting to a typed route, as {@code <wo:routeForm>}, see docs/ROUTING.md.
 *
 * <pre>
 * &lt;wo:routeForm to="$createBook"&gt;
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

	/**
	 * The query of a get form's action while it's rendered, for its hidden fields: this element's, so a form inside it
	 * doesn't replace it
	 */
	private final ThreadLocal<String> _query = new ThreadLocal<>();

	public ERXRouteForm( final String name, final NSDictionary associations, final WOElement template ) {
		super( name, RouteBindings.withRouteURL( associations, "<wo:routeForm>", "<wo:form>", URL_KEYS ), template );

		// Read now: the associations left for attributes are let go of after the element is built
		_method = _associations.objectForKey( "method" );
		_href = new GetFormURL( _href );
	}

	@Override
	public void appendToResponse( final WOResponse response, final WOContext context ) {

		// A render that threw before its children left no query for this one, whose action may not be rendered (disabled)
		_query.remove();
		super.appendToResponse( response, context );
	}

	@Override
	public void appendChildrenToResponse( final WOResponse response, final WOContext context ) {
		super.appendChildrenToResponse( response, context );

		// The query the action's URL had, if it's a get form's: this form's action, rendered just before in this thread (a
		// form inside it, embedded, has its own)
		final String query = _query.get();
		_query.remove();

		if( query == null ) {
			return;
		}

		for( final String pair : query.split( "&" ) ) {
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

	/**
	 * The route's URL as a form's action: a get form's without its query, which a browser would replace with the form's
	 * fields, so the query is kept for the form's hidden fields (the URL generated once)
	 */
	private class GetFormURL extends WOAssociation {

		private final WOAssociation _url;

		private GetFormURL( final WOAssociation url ) {
			_url = url;
		}

		@Override
		public Object valueInComponent( final WOComponent component ) {
			final String url = String.valueOf( _url.valueInComponent( component ) );
			final int q = url.indexOf( '?' );
			_query.remove();

			if( q == -1 || _method == null || !"get".equalsIgnoreCase( String.valueOf( _method.valueInComponent( component ) ) ) ) {
				return url;
			}

			// The URL is an attribute's value, its & escaped
			_query.set( url.substring( q + 1 ).replace( "&amp;", "&" ) );
			return url.substring( 0, q );
		}

		@Override
		public String keyPath() {
			return _url.keyPath();
		}

		@Override
		public String bindingInComponent( final WOComponent component ) {
			return _url.bindingInComponent( component );
		}
	}
}
