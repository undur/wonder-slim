package bookclubs.components;

import java.util.List;

import com.webobjects.appserver.WOContext;

import bookclubs.data.Library;
import bookclubs.data.Library.Club;

/**
 * The landing page: every club, each linking to its home
 */
public class Main extends BaseComponent {

	public Club current;

	public Main( final WOContext context ) {
		super( context );
	}

	public List<Club> clubs() {
		return Library.clubs();
	}
}
