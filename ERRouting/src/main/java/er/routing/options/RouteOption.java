package er.routing.options;

/**
 * Something a route is declared with besides its pattern and handler: a condition on the request
 * ({@link RouteCondition}: {@link Host}, {@link Method}) or a trailing slash policy ({@link TrailingSlash}).
 *
 * <pre>
 * routes.map( "/hooks/github", hooks::github, Method.POST );
 * routes.map( "/rules/", rules, TrailingSlash.REDIRECT );
 * </pre>
 */

public interface RouteOption {}
