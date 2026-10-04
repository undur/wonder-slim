package er.extensions.routing;

import java.util.List;

import er.routing.options.CrossSite;
import er.routing.options.Fields;
import er.routing.options.RouteCondition;
import er.routing.options.TrailingSlash;



/**
 * A mapped route, as {@link ERXRouter#routes()} lists it (for a page showing the
 * application's routes, say).
 *
 * @param pattern The route's whole path pattern
 * @param conditions Its conditions (host, methods)
 * @param trailingSlash Its own trailing slash policy, null for the router's
 * @param table The table it's mapped in ({@code application}, or a plugin's)
 * @param route The route, for linking to it
 * @param parametersClass A typed route's record class, null for a plain route
 * @param crossSite The sites it takes requests that change things from
 * @param fieldsReported true if fields that don't convert are reported to it ({@link Fields#REPORTED})
 * @param answer What answers it: a page's name, or a handler's class (a lambda's is "a handler in" its class)
 * @param page true if a page answers it (a page route), false for a handler
 */

public record RouteDescription( String pattern, List<RouteCondition> conditions, TrailingSlash trailingSlash, String table, Linkable route, Class<? extends Record> parametersClass, CrossSite crossSite, boolean fieldsReported, String answer, boolean page ) {

	/**
	 * @return The name of the constant holding the route ({@code Routes.book}), null for a route nothing links to by a
	 *         constant
	 */
	public String constant() {
		return route instanceof RouteIdentity<?> identity ? identity.constantNameOrNull() : null;
	}
}
