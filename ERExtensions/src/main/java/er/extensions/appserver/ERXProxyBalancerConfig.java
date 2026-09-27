package er.extensions.appserver;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOCookie;
import com.webobjects.appserver.WOCookie.SameSite;
import com.webobjects.foundation.NSNotification;

/**
 * The route cookie for sticky sessions behind Apache's mod_proxy_balancer.
 *
 * With several instances of an application behind mod_proxy_balancer (in place of the WebObjects adaptor), each instance
 * is a balancer member with a route, and the balancer keeps a session on its instance by reading the route from a cookie.
 * JavaMonitor's mod_proxy page generates the matching Apache configuration:
 *
 * <pre>
 * &lt;Proxy balancer://App.woa&gt;
 * 	BalancerMember http://host:2001/cgi-bin/WebObjects/App.woa route=app_2001
 * 	BalancerMember http://host:2002/cgi-bin/WebObjects/App.woa route=app_2002
 * &lt;/Proxy&gt;
 * ProxyPass /cgi-bin/WebObjects/App.woa balancer://App.woa stickysession=routeid_app nofailover=On
 * </pre>
 *
 * Every instance answers with the cookie {@code routeid_<app>=.<app>_<port>} (name and port lowercased, dots in the name
 * replaced by underscores). The leading dot matters: mod_proxy_balancer takes the route from the part of the sticky value
 * after the first dot (the {@code JSESSIONID=<id>.<route>} convention), so a value without one names no route and
 * requests aren't kept on their instance. The cookie's path is "/", or the FixCookiePath system property.
 *
 * The cookie is added on {@code DidHandleRequestNotification}, which the request handlers for pages, actions, routes
 * and Ajax requests post, so it rides on the responses sessions live on. Responses from handlers that don't post it,
 * such as the framework's resource handler, don't carry it (a response setting a cookie isn't stored by shared caches).
 * Without a balancer the cookie is simply ignored.
 *
 * @param route The instance's route, as named in the balancer member's {@code route=}
 * @param cookieName The sticky session cookie's name, as named in the balancer's {@code stickysession=}
 * @param cookiePath The cookie's path
 */
public record ERXProxyBalancerConfig( String route, String cookieName, String cookiePath ) {

	/**
	 * The configuration for an instance of the named application on the given port
	 */
	public ERXProxyBalancerConfig( String applicationName, Number instancePortNumber ) {
		final String fixCookiePathProperty = System.getProperty("FixCookiePath");
		final String proxyBalancerRoute = (applicationName + "_" + instancePortNumber.toString()).toLowerCase().replace('.', '_');
		final String proxyBalancerCookieName = ("routeid_" + applicationName).toLowerCase().replace('.', '_');
		final String proxyBalancerCookiePath = fixCookiePathProperty != null ? fixCookiePathProperty : "/";
		this(proxyBalancerRoute, proxyBalancerCookieName, proxyBalancerCookiePath);
	}

	/**
	 * Invoked on DidHandleRequestNotification to add the "balancer route cookie" to the current context's response 
	 */
	public void addBalancerRouteCookieByNotification(final NSNotification notification) {
		if (notification.object() instanceof WOContext context) {
			if (context.request() != null && context.response() != null) {
				context.response().addCookie( createCookie( context.request().isSecure() ) );
			}
		}
	}

	/**
	 * @return The cookie's value: the route after a dot, which is where mod_proxy_balancer reads the route from
	 */
	String cookieValue() {
		return "." + route;
	}

	/**
	 * @return A new balancer route cookie
	 */
	WOCookie createCookie( final boolean secure ) {
		final WOCookie cookie = new WOCookie(cookieName, cookieValue(), cookiePath, null, -1, secure, true);
		cookie.setExpires(null);
		cookie.setSameSite(SameSite.LAX);
		return cookie;
	}
}
