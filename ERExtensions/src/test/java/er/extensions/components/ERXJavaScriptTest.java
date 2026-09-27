package er.extensions.components;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver._private.WODynamicElementCreationException;
import com.webobjects.appserver._private.WODynamicGroup;
import com.webobjects.appserver._private.WOHTMLBareString;
import com.webobjects.foundation.NSMutableDictionary;

/**
 * What ERXJavaScript accepts when a template is parsed
 */
public class ERXJavaScriptTest {

	private static ERXJavaScript script( final WOElement template, final String... bindings ) {
		final NSMutableDictionary<String, WOAssociation> associations = new NSMutableDictionary<>();

		for( int i = 0; i < bindings.length; i += 2 ) {
			associations.setObjectForKey( WOAssociation.associationWithValue( bindings[i + 1] ), bindings[i] );
		}

		return new ERXJavaScript( "ERXJavaScript", associations, template );
	}

	@Test
	public void referencesOneScript() {
		assertDoesNotThrow( () -> script( null, "filename", "app.js" ) );
		assertDoesNotThrow( () -> script( null, "filename", "app.js", "framework", "AjaxSlim", "defer", "defer", "type", "module" ) );
		assertDoesNotThrow( () -> script( null, "filename", "https://cdn.example/lib.js", "integrity", "sha384-abc" ) );

		assertThrows( WODynamicElementCreationException.class, () -> script( null ) );
	}

	@Test
	public void noContent() {
		// Parsley passes an empty group for an element without content, WO's own parser passes null
		assertDoesNotThrow( () -> script( new WODynamicGroup( null, null, (WOElement)null ), "filename", "app.js" ) );

		final WODynamicElementCreationException e = assertThrows( WODynamicElementCreationException.class, () -> script( new WOHTMLBareString( "alert(1)" ), "filename", "app.js" ) );
		assertTrue( e.getMessage().contains( "<script>" ), e.getMessage() );
	}

	@Test
	public void removedBindingsThrow() {
		for( final String removed : new String[] { "scriptKey", "scriptString", "scriptFile", "hideInComment" } ) {
			assertThrows( WODynamicElementCreationException.class, () -> script( null, "filename", "app.js", removed, "x" ), removed );
		}
	}

	@Test
	public void deprecatedNamesAreAccepted() {
		assertDoesNotThrow( () -> script( null, "scriptSource", "app.js" ) );
		assertDoesNotThrow( () -> script( null, "scriptSource", "app.js", "scriptFramework", "AjaxSlim" ) );
		assertThrows( WODynamicElementCreationException.class, () -> script( null, "scriptFramework", "AjaxSlim" ) );
	}
}
