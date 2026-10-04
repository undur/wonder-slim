/**
 * Everything passed to a route or a group: conditions on the request (the host, methods,
 * the scheme, headers), the trailing slash policy, and behaviors (how fields that don't convert are handled, which sites
 * may post, which sites' scripts may call). {@link er.routing.options.RouteCondition} and
 * {@link er.routing.options.RouteRequest} are for conditions of an application's own.
 *
 * Free of WebObjects, so it can be shared with ng-objects.
 */
package er.routing.options;
