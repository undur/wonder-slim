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
}
