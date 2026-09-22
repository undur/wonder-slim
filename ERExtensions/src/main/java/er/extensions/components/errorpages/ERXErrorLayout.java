package er.extensions.components.errorpages;

import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXStatelessComponent;

/**
 * The shared skeleton of the framework's user-facing error pages (session expired, page no longer
 * available, session creation refused, generic error): a centered card with an icon, a heading, the
 * page's own explanation as wrapped content, and the application's name.
 *
 * Deliberately self-contained - inline CSS, an inline SVG icon, no web resources, no scripts. These
 * pages render when something has already gone wrong, so they must not depend on anything that could
 * be part of what went wrong.
 *
 * Bindings: [title] the document title (defaults to the heading), [heading] the card's heading.
 * The explanation and actions go inside as content.
 */
public class ERXErrorLayout extends ERXStatelessComponent {

	public ERXErrorLayout( WOContext context ) {
		super( context );
	}

	public String title() {
		return stringValueForBinding( "title", heading() );
	}

	public String heading() {
		return stringValueForBinding( "heading", "Something went wrong" );
	}
}
