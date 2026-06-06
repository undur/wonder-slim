package ajaxplayground.components.gallery;

import java.util.Arrays;
import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Gallery: wonder-select, the morph-native Chosen replacement.
 *
 * The select lives INSIDE a morphing AjaxUpdateContainer that can be refreshed on demand - the
 * exact situation that breaks Chosen (preserved select, stripped .chosen-container, re-init no-op).
 * wonder-select must survive the morph: the widget stays usable, the selected value is preserved,
 * and picking an option still fires a native change (shown here by a server-side echo of the
 * current selection that updates via an observe field).
 */
public class GalleryWonderSelect extends PlaygroundPage {

	public String currentFruit;
	public String selectedFruit;
	private int _refreshCount;

	/** Second case: a select whose OWN container is refreshed by its change (the Strimillinn case). */
	public String selfFruit;
	private int _selfCount;

	public GalleryWonderSelect( WOContext context ) {
		super( context );
	}

	public List<String> fruits() {
		return Arrays.asList( "Apple", "Apricot", "Banana", "Blackberry", "Blueberry", "Cherry", "Date", "Elderberry", "Fig", "Grape", "Mango", "Orange", "Peach", "Pear", "Plum", "Raspberry", "Strawberry" );
	}

	public String selectedFruit() {
		return selectedFruit == null ? "(none)" : selectedFruit;
	}

	public int refreshCount() {
		return _refreshCount;
	}

	/** Picking a fruit (native change -> observe field) lands here. */
	public WOActionResults fruitChanged() {
		return null;
	}

	/** Explicitly morph the container holding the select, to prove the widget survives. */
	public WOActionResults refreshContainer() {
		_refreshCount++;
		return null;
	}

	public int selfCount() {
		return _selfCount;
	}

	/** The self-refreshing select's change: bumps a counter and morphs its OWN container. */
	public WOActionResults selfChanged() {
		_selfCount++;
		return null;
	}
}
