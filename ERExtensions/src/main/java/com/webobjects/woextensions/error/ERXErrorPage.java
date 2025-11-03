package com.webobjects.woextensions.error;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;

import er.extensions.components.ERXNonSynchronizingComponent;

public class ERXErrorPage extends ERXNonSynchronizingComponent {

	private String _message;

	public ERXErrorPage( WOContext context ) {
		super( context );
	}

	public static WOResponse errorWithMessageAndStatusCode( String message, WOContext context, int status ) {
		ERXErrorPage nextPage = (ERXErrorPage)WOApplication.application().pageWithName( ERXErrorPage.class.getSimpleName(), context );
		nextPage.setMessage( message );
		WOResponse r = nextPage.generateResponse();
		r.setStatus( status );
		return r;
	}

	public static WOResponse handleSessionRestorationErrorInContext( WOContext context ) {
		String appPath = "/Apps" + WOApplication.application().baseURL();
		int sessionTimeoutInMinutes = WOApplication.application().sessionTimeOut().intValue() / 60;
		String s = "Session has expired. The maximum period of inactivity before session termination is " + sessionTimeoutInMinutes + " minutes. Click <a href=\"" + appPath + "\" target=\"top\">here</a> to reconnect.";
		return errorWithMessageAndStatusCode( s, context, 403 );
	}

	public String message() {
		return _message;
	}

	private void setMessage( String value ) {
		_message = value;
	}
}