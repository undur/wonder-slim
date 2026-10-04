/**
 * What an application writes its routes against: the router ({@link er.extensions.routing.ERXRouter}), groups, routes
 * and their invocations. See docs/ROUTING.md.
 *
 * The options routes take are in {@link er.routing.options}, converters in {@link er.routing.conversion} (the routing
 * core, a plain Java library), and the template elements in {@code er.extensions.components.additions}
 * ({@code <wo:route>}, {@code <wo:routeForm>}). The implementation lives here too, package-private: what isn't
 * public here isn't the API.
 */
package er.extensions.routing;
