package er.extensions.components.routing;

import java.util.List;

import com.webobjects.appserver.WOElement;
import com.webobjects.foundation.NSDictionary;

import er.extensions.components.patches.ERXWOHyperlink;
import er.extensions.routing.Route;

/**
 * A link to an {@link Route}, as {@code <wo:route>}, see docs/ROUTE_LINKS.md.
 *
 * <pre>
 * &lt;wo:route to="$search" :area="bork" :q="$someString"&gt;Search&lt;/wo:route&gt;
 * </pre>
 *
 * {@code route} binds an {@link Route}, and each {@code :name} binding one of its parameters. The URL is the
 * route's URL for those parameters: an unknown parameter, a missing path parameter or a value of the wrong type is an
 * error when the link renders. {@code ?} bindings are added to the query as on any hyperlink. The URL comes from the
 * route only, so {@code href}, {@code action}, {@code pageName} and the direct action bindings aren't accepted.
 */

public class ERXRouteHyperlink extends ERXWOHyperlink {

	private static final List<String> URL_KEYS = List.of( "href", "action", "directActionName", "actionClass", "pageName" );

	public ERXRouteHyperlink( final String name, final NSDictionary associations, final WOElement template ) {
		super( name, RouteBindings.withRouteURL( associations, "<wo:route>", "<wo:link>", URL_KEYS ), template );
	}
}
