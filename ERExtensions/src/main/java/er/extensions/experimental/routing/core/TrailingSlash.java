package er.extensions.experimental.routing.core;

/**
 * What happens to a request whose path matches a route's pattern only in the other trailing slash form ({@code /docs}
 * for {@code /docs/}). A pattern declares its form, and generated URLs use it. The root ({@code /}) has one form only.
 */

public enum TrailingSlash {

	/**
	 * Both forms match
	 */
	IGNORE,

	/**
	 * The other form is answered with {@code 308} to the declared form, which keeps the method and the body
	 */
	REDIRECT,

	/**
	 * Only the declared form matches
	 */
	STRICT
}
