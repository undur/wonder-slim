package er.routing;

import java.util.Map;

import com.webobjects.appserver.WOContext;

/**
 * EXPERIMENTAL (route-links branch). A route whose parameters are its pattern's and its host's, given by name, and which
 * does what its handler (or page) does. A constant ({@link Route#plain()}) gets its pattern from a route declaration:
 *
 * <pre>
 * PlainRoute rules = Route.plain();
 *
 * club.map( "/rules/", Routes.rules, ri -&gt; … );
 * return Routes.rules.redirect( context );
 * </pre>
 *
 * Its parameters are converted by the router's converters. Query parameters are added to a link with {@code ?}
 * attributes.
 */

public final class PlainRoute extends RouteIdentity<PlainBinding> implements Linkable {

	PlainRoute( final Class<?> madeIn ) {
		super( madeIn );
	}

	/**
	 * @return The whole path pattern, the group's prefix included
	 */
	public String pattern() {
		return binding().pattern();
	}

	/**
	 * @throws IllegalArgumentException for a missing route parameter, or a wildcard route (which has no URL of its own)
	 */
	@Override
	public String url( final Map<String, Object> values, final WOContext context ) {
		return binding().url( values, context );
	}

	@Override
	public String completeURL( final Map<String, Object> values ) {
		return binding().completeURL( values );
	}

	@Override
	String description() {
		return "Route.plain()";
	}

	@Override
	public String toString() {
		try {
			return "PlainRoute " + binding();
		}
		catch( IllegalStateException e ) {
			return "PlainRoute " + name();
		}
	}
}
