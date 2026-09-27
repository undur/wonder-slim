package er.extensions.components.errorpages;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;

import er.extensions.components.ERXNonSynchronizingComponent;

/**
 * A general-purpose error page for applications: a message on the framework's error page layout ({@link ERXErrorLayout},
 * self-contained, so it renders whatever else has gone wrong), with a button back to the application's default direct
 * action. Return it from an action, or from an override of one of WOApplication's error handlers:
 *
 * <pre><code>return ERXErrorPage.errorWithMessageAndStatusCode( "That invoice doesn't exist.", context(), 404 );</code></pre>
 *
 * The message is rendered as HTML, unescaped, so it can carry markup. Escape anything in it that comes from a user.
 */
public class ERXErrorPage extends ERXNonSynchronizingComponent {

	private String _message;

	public ERXErrorPage( WOContext context ) {
		super( context );
	}

	/**
	 * @param message The message to show, as HTML (see the class documentation)
	 * @param status The response's HTTP status
	 * @return A response showing the message on the error page, with the given status
	 */
	public static WOResponse errorWithMessageAndStatusCode( String message, WOContext context, int status ) {
		ERXErrorPage nextPage = (ERXErrorPage)WOApplication.application().pageWithName( ERXErrorPage.class.getSimpleName(), context );
		nextPage.setMessage( message );
		WOResponse r = nextPage.generateResponse();
		r.setStatus( status );
		return r;
	}

	/**
	 * A session expiry response, for an application's override of WOApplication.handleSessionRestorationErrorInContext():
	 * the error page telling the user their session expired, and after how long, with status 403. The page's button
	 * takes them back to the application.
	 */
	public static WOResponse handleSessionRestorationErrorInContext( WOContext context ) {
		int sessionTimeoutInMinutes = WOApplication.application().sessionTimeOut().intValue() / 60;
		String s = "Your session has expired. Sessions end after " + sessionTimeoutInMinutes + " minutes without activity.";
		return errorWithMessageAndStatusCode( s, context, 403 );
	}

	/**
	 * @return The message, as HTML
	 */
	public String message() {
		return _message;
	}

	private void setMessage( String value ) {
		_message = value;
	}
}