package er.routing;

import java.util.List;

import com.webobjects.appserver.WOElement;
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
 */

public class ERXRouteForm extends ERXWOForm {

	private static final List<String> URL_KEYS = List.of( "href", "action", "directActionName", "actionClass" );

	public ERXRouteForm( final String name, final NSDictionary associations, final WOElement template ) {
		super( name, RouteBindings.withRouteURL( associations, "<wo:routeForm>", "<wo:form>", URL_KEYS ), template );
	}
}
