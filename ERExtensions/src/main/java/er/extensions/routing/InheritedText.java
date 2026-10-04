package er.extensions.routing;

/**
 * A host parameter a link didn't give, taken from the request's host: already URL text, so it's used as it is, without
 * converting it to its type and back for each link
 */

record InheritedText( String text ) {}
