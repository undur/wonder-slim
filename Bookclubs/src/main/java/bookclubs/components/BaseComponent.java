package bookclubs.components;

import com.webobjects.appserver.WOContext;

import bookclubs.Routes;
import bookclubs.data.Library.Club;
import er.extensions.components.ERXComponent;

/**
 * The application's pages: each knows its club, and links to the routes ({@link Routes}) as {@code $routes.…}
 */
public abstract class BaseComponent extends ERXComponent implements Routes {

	public Club club;

	public BaseComponent( final WOContext context ) {
		super( context );
	}

	/**
	 * Sets the page's club
	 */
	@SuppressWarnings("unchecked")
	public <T extends BaseComponent> T club( final Club club ) {
		this.club = club;
		return (T)this;
	}
}
