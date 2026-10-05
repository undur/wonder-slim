package ajaxplayground;

import er.extensions.appserver.ERXSession;

/**
 * Bare session - the playground keeps no user/login state. IDs are stored in cookies (not URLs) so
 * that page URLs stay clean and stable, which matters for driving pages from a headless browser.
 */
public class Session extends ERXSession {

	public Session() {
		setStoresIDsInCookies( true );
		setStoresIDsInURLs( false );
	}

	@Override
	public String domainForIDCookies() {
		return "/";
	}
}
