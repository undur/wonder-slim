package er.extensions.routing;

/**
 * Thrown for a route parameter whose text isn't its value's canonical text ({@code 007} for 7): the router answers with
 * a redirect to the URL with the canonical text, so each object has one URL.
 */

class NotCanonical extends RuntimeException {

	final String name;
	final String canonicalText;

	NotCanonical( final String name, final String canonicalText ) {
		super( null, null, false, false );
		this.name = name;
		this.canonicalText = canonicalText;
	}
}
