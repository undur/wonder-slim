package bookclubs.components;

import com.webobjects.appserver.WOContext;

import bookclubs.BookclubRoutes;
import bookclubs.data.Library.Club;
import er.extensions.components.ERXComponent;

/**
 * The application's pages: each knows its club, and reaches the routes as {@code $routes}
 */
public abstract class BaseComponent extends ERXComponent {

	public Club club;

	public BaseComponent( final WOContext context ) {
		super( context );
	}

	public BookclubRoutes routes() {
		return BookclubRoutes.instance();
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
