package er.extensions.appserver;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOCookie;
import com.webobjects.appserver.WOCookie.SameSite;
import com.webobjects.foundation.NSNotification;

/**
 * Configuration for the generation of the proxy balancer cookie 
 */
public record ERXProxyBalancerConfig( String route, String cookieName, String cookiePath ) {
	
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
	 * @return A new balancer route cookie
	 */
	private WOCookie createCookie( final boolean secure ) {
		final WOCookie cookie = new WOCookie(cookieName, route, cookiePath, null, -1, secure, true);
		cookie.setExpires(null);
		cookie.setSameSite(SameSite.LAX);
		return cookie;
	}
}