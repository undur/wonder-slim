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
 * What ERXStyleSheet accepts when a template is parsed
 */
public class ERXStyleSheetTest {

	private static ERXStyleSheet styleSheet( final WOElement template, final String... bindings ) {
		final NSMutableDictionary<String, WOAssociation> associations = new NSMutableDictionary<>();

		for( int i = 0; i < bindings.length; i += 2 ) {
			associations.setObjectForKey( WOAssociation.associationWithValue( bindings[i + 1] ), bindings[i] );
		}

		return new ERXStyleSheet( "ERXStyleSheet", associations, template );
	}

	@Test
	public void referencesOneStylesheet() {
		assertDoesNotThrow( () -> styleSheet( null, "filename", "a.css" ) );
		assertDoesNotThrow( () -> styleSheet( null, "filename", "a.css", "framework", "AjaxSlim", "media", "print" ) );
		assertDoesNotThrow( () -> styleSheet( null, "href", "https://cdn.example/a.css", "integrity", "sha384-abc", "crossorigin", "anonymous" ) );

		assertThrows( WODynamicElementCreationException.class, () -> styleSheet( null ) );
		assertThrows( WODynamicElementCreationException.class, () -> styleSheet( null, "filename", "a.css", "href", "/a.css" ) );
	}

	@Test
	public void noContent() {
		// Parsley passes an empty group for an element without content, WO's own parser passes null
		assertDoesNotThrow( () -> styleSheet( new WODynamicGroup( null, null, (WOElement)null ), "href", "/a.css" ) );

		final WODynamicElementCreationException e = assertThrows( WODynamicElementCreationException.class, () -> styleSheet( new WOHTMLBareString( "body { color: red }" ), "href", "/a.css" ) );
		assertTrue( e.getMessage().contains( "<style>" ), e.getMessage() );
	}

	@Test
	public void removedBindingsThrow() {
		assertThrows( WODynamicElementCreationException.class, () -> styleSheet( null, "href", "/a.css", "key", "k" ) );
		assertThrows( WODynamicElementCreationException.class, () -> styleSheet( null, "styleSheetUrl", "/a.css" ) );
		assertThrows( WODynamicElementCreationException.class, () -> styleSheet( null, "styleSheetName", "a.css" ) );
		assertThrows( WODynamicElementCreationException.class, () -> styleSheet( null, "filename", "a.css", "styleSheetFrameworkName", "AjaxSlim" ) );
	}
}
