package ajaxplayground.components.gallery;

import java.util.ArrayList;
import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Gallery: AjaxPopUpButton with a LARGE option list - the render-performance stress case.
 *
 * Opening the dropdown builds one &lt;li&gt; per option. With a few hundred options the naive
 * append-per-option / O(n^2) selected-check path took ~1-2s to open (much worse than Chosen). This
 * page exists to measure and guard that: the bridge opens the list and asserts both that all options
 * rendered and that the open completed quickly.
 */
public class GalleryLargeList extends PlaygroundPage {

	public String currentItem;
	public String selectedItem;
	private List<String> _items;

	public GalleryLargeList( WOContext context ) {
		super( context );
	}

	/** A couple thousand distinct, realistically-long labels (the worst-case accounting-key use case). */
	public List<String> items() {
		if( _items == null ) {
			List<String> list = new ArrayList<>();
			for( int i = 1; i <= 2000; i++ ) {
				list.add( String.format( "%04d - Account ledger entry for cost centre %d", i, i ) );
			}
			_items = list;
		}
		return _items;
	}

	public int itemCount() {
		return items().size();
	}

	public WOActionResults selectionChanged() {
		return null;
	}
}
