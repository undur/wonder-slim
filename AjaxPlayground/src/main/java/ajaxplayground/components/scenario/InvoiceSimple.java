package ajaxplayground.components.scenario;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSArray;

import ajaxplayground.components.PlaygroundPage;
import er.ajax.AjaxSortable;

/**
 * A small, clear invoice editor that shows the AjaxSlim essentials without the heavy testing scaffolding
 * of the {@link ScenarioInvoice} stress page: a customer picker (wonder-select), a multi-select for tags,
 * drag-to-reorder lines whose quantity / unit price are edited inline, and a live preview. A single
 * descendant-observing AjaxObserveField wraps the whole editor and refreshes the line items, the totals
 * and the preview together whenever anything changes.
 * <p>
 * It's a showcase, but also a test in its own right: it exercises the common path - one observe field
 * driving a multi-container morph, plus the two select widgets and drag-to-reorder - so a regression in
 * the everyday case shows up here, plainly, where {@link ScenarioInvoice} would only say "something is
 * off". Simplicity needs coverage too.
 */
public class InvoiceSimple extends PlaygroundPage {

	public InvoiceLine currentLine;
	public String currentCustomer;
	public String currentProduct;
	public String currentTag;
	public String customer;
	public NSArray<String> tags;

	private final List<InvoiceLine> _lines;

	public InvoiceSimple(WOContext context) {
		super(context);
		_lines = new ArrayList<>();
		_lines.add(new InvoiceLine(1, "Widget", 2, 500));
		_lines.add(new InvoiceLine(2, "Gadget", 1, 1200));
		_lines.add(new InvoiceLine(3, "Sprocket", 4, 75));
		customer = "Acme Corp";
		tags = new NSArray<>();
	}

	public List<InvoiceLine> lines() {
		return _lines;
	}

	public List<String> customers() {
		return Arrays.asList("Acme Corp", "Globex", "Initech", "Umbrella", "Wayne Enterprises", "Stark Industries");
	}

	public List<String> products() {
		return Arrays.asList("Widget", "Gadget", "Sprocket", "Cog", "Flange", "Grommet", "Bracket", "Bearing");
	}

	public List<String> allTags() {
		return Arrays.asList("urgent", "wholesale", "tax-exempt", "recurring", "international");
	}

	public String qtyFieldID() {
		return "qty-" + currentLine.id();
	}

	public String priceFieldID() {
		return "price-" + currentLine.id();
	}

	public String productFieldID() {
		return "product-" + currentLine.id();
	}

	public int grandTotal() {
		int sum = 0;
		for (InvoiceLine line : _lines) {
			sum += line.lineTotal();
		}
		return sum;
	}

	public boolean hasTags() {
		return tags != null && tags.count() > 0;
	}

	public String tagsDisplay() {
		return tags == null ? "" : tags.componentsJoinedByString(", ");
	}

	public WOActionResults edited() {
		return null;
	}

	public WOActionResults reorderLines() {
		AjaxSortable.DragResult move = AjaxSortable.dragResult(context().request());
		if (move != null && move.isValidFor(_lines.size())) {
			_lines.add(move.toIndex(), _lines.remove(move.fromIndex()));
		}
		return null;
	}
}
