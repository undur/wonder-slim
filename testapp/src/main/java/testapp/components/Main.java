package testapp.components;

import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXComponent;

public class Main extends ERXComponent {

	public String username;

	public Main( WOContext context ) {
		super( context );
	}
}