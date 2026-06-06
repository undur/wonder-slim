package ajaxplayground.components.gallery;

import java.util.Arrays;
import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Gallery: AjaxPopUpButton - the explicit element form of wonder-select.
 *
 * Same widget as GalleryWonderSelect, but authored as <wo:AjaxPopUpButton ...> with NO class and NO
 * manual resource include - the element adds the marker class and pulls in wonder-select.js/.css
 * itself. Proves the element is a drop-in for popUpButton that "just works", including surviving a
 * morph of the enclosing container.
 */
public class GalleryAjaxPopUpButton extends PlaygroundPage {

	public String currentFruit;
	public String selectedFruit;
	private int _refreshCount;
	private boolean _submitted;

	public GalleryAjaxPopUpButton( WOContext context ) {
		super( context );
	}

	public List<String> fruits() {
		return Arrays.asList( "Apple", "Apricot", "Banana", "Blackberry", "Blueberry", "Cherry", "Date", "Elderberry", "Fig", "Grape", "Mango", "Orange", "Peach", "Pear", "Plum", "Raspberry", "Strawberry" );
	}

	public int refreshCount() {
		return _refreshCount;
	}

	public WOActionResults fruitChanged() {
		return null;
	}

	public WOActionResults refreshContainer() {
		_refreshCount++;
		return null;
	}

	/** Submit button action - lets the bridge confirm Enter-on-trigger really submits the form. */
	public WOActionResults doSubmit() {
		_submitted = true;
		return null;
	}

	public String submittedFlag() {
		return _submitted ? "YES" : "no";
	}
}

