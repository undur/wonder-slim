package er.extensions.foundation;

import er.extensions.appserver.ERXWOContext;

// FIXME: Just here as a placeholder so older APIs work during transition from Wonder 7.2

@Deprecated
public class ERXStringUtilities {

	@Deprecated
	public static String safeIdentifierName(String a, String b) {
		return ERXWOContext.safeIdentifierName(a, b);
	}
}