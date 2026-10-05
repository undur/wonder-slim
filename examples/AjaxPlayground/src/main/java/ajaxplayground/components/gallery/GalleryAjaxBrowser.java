package ajaxplayground.components.gallery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Gallery: AjaxBrowser - the multi-select wonder-select widget.
 *
 * Mirrors how a real multi-select is used (object list + item + displayString + a List-of-objects
 * selections binding) - the configuration WOBrowser round-trips correctly. (A bare String list with
 * no value binding does NOT round-trip through WOBrowser, which is a WO quirk, not an AjaxBrowser
 * issue.) Verifies the multi path of wonder-select: tags, toggle, remove, native change reaching the
 * server, morph-survival.
 */
public class GalleryAjaxBrowser extends PlaygroundPage {

	/** A simple object so the list items are objects (like the real usage), not bare Strings. */
	public static class Fruit {
		public final String name;
		Fruit( String name ) { this.name = name; }
		public String name() { return name; }
		@Override public String toString() { return name; }
	}

	private static final List<Fruit> ALL = new ArrayList<>();
	static {
		for( String n : Arrays.asList( "Apple", "Apricot", "Banana", "Blackberry", "Blueberry", "Cherry", "Date", "Elderberry", "Fig", "Grape", "Mango", "Orange", "Peach", "Pear", "Plum", "Raspberry", "Strawberry" ) ) {
			ALL.add( new Fruit( n ) );
		}
	}

	public Fruit currentFruit;
	public List<Fruit> selectedFruits = new ArrayList<>();
	private int _changeCount;

	public GalleryAjaxBrowser( WOContext context ) {
		super( context );
	}

	public List<Fruit> fruits() {
		return ALL;
	}

	/** A readable echo of the current multi-selection so the server side is visible in tests. */
	public String selectedEcho() {
		if( selectedFruits == null || selectedFruits.isEmpty() ) {
			return "(none)";
		}
		List<String> names = new ArrayList<>();
		for( Fruit f : selectedFruits ) {
			names.add( f.name );
		}
		return String.join( ", ", names );
	}

	public int changeCount() {
		return _changeCount;
	}

	public WOActionResults selectionChanged() {
		_changeCount++;
		return null;
	}

	public WOActionResults refreshContainer() {
		_changeCount++;
		return null;
	}
}
