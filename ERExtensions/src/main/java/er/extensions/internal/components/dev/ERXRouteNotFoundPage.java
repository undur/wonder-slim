package er.extensions.internal.components.dev;

import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXComponent;

/**
 * What a URL no route claims shows in development: the URL, the routes that are mapped, and how to map one. Deployed,
 * it's a plain 404.
 */
public class ERXRouteNotFoundPage extends ERXComponent {

	private String _url;

	public ERXRouteNotFoundPage( final WOContext context ) {
		super( context );
	}

	public String url() {
		return _url;
	}

	public void setURL( final String url ) {
		_url = url;
	}

	/**
	 * @return Why the routes that matched the URL passed it on ({@link er.extensions.routes.RouteTable#DECLINES_KEY}),
	 *         empty if none did
	 */
	@SuppressWarnings("unchecked")
	public java.util.List<String> declines() {
		final Object declines = context().request().userInfoForKey( er.extensions.routes.RouteTable.DECLINES_KEY );
		return declines == null ? java.util.List.of() : (java.util.List<String>)declines;
	}

	public String decline;

	public boolean hasDeclines() {
		return !declines().isEmpty();
	}
}
