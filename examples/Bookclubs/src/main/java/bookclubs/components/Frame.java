package bookclubs.components;

import com.webobjects.appserver.WOContext;

import bookclubs.Routes;
import bookclubs.data.Library.Club;
import er.extensions.components.ERXNonSynchronizingComponent;

/**
 * The frame around every page: the club's name and navigation, all of it links to routes
 */
public class Frame extends ERXNonSynchronizingComponent implements Routes {

	public Frame( final WOContext context ) {
		super( context );
	}

	public Club club() {
		return (Club)valueForBinding( "club" );
	}

	public String title() {
		return (String)valueForBinding( "title" );
	}
}
