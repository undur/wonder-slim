package er.extensions.admin;

import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXComponent;
import er.extensions.dev.ERXConsoleCapture;

/**
 * The tail of what the application has logged, from the console capture's ring buffer.
 */
public class ERXAdminLogPage extends ERXComponent {

	public String filter;
	public int tail = 300;

	public ERXAdminLogPage( WOContext context ) {
		super( context );
	}

	public boolean isCapturing() {
		return ERXConsoleCapture.isInstalled();
	}

	public String log() {
		final List<String> lines = ERXConsoleCapture.snapshot( filter == null || filter.isBlank() ? null : filter, tail );
		return String.join( "\n", lines );
	}

	public WOActionResults apply() {
		if( tail < 1 ) {
			tail = 300;
		}

		return null;
	}

	public WOActionResults startCapturing() {
		ERXConsoleCapture.install();
		return null;
	}
}
