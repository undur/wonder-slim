package x;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOSession;
import com.webobjects.appserver.WOSessionStore;

/**
 * This thing is experimental, trying out a ConcurrentHashMap.
 */

public class ERXServerSessionStore extends WOSessionStore {

	private final Map<String, WOSession> _sessions = new ConcurrentHashMap<>(1024);

	public WOSession removeSessionWithID(String sessionID) {
		System.out.println( "removeSessionWithID" );
		return _sessions.remove(sessionID);
	}

	public WOSession restoreSessionWithID(String sessionID, WORequest aRequest) {
		System.out.println( "restoreSessionWithID" );
		return _sessions.get(sessionID);
	}

	public void saveSessionForContext(WOContext context) {
		System.out.println( "saveSessionForContext" );
		WOSession session = context._session();

		try {
			_sessions.put(session.sessionID(), session);
		}
		catch (NullPointerException e) {
			throw new IllegalStateException(this.toString() + ": Cannot save session because the current context has no session.");
		}
	}
}