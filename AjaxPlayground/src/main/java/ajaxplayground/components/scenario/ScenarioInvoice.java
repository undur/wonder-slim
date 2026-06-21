package ajaxplayground.components.scenario;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSArray;

import ajaxplayground.components.PlaygroundPage;
import er.ajax.elements.AjaxSortable;

/**
 * Integration scenario: a working invoice editor.
 *
 * This page is deliberately NOT a test grid - it is a small but real-feeling application that happens
 * to exercise EVERY AjaxSlim element together, so behavioural conflicts surface as something that
 * feels "wrong" (a total that doesn't add up, a row's data on the wrong line, a validation message
 * that vanishes) rather than as an abstract assertion failure. The grand total is the canary: it is a
 * sum of values the user is editing through several different elements at once, so almost any
 * cross-element regression makes it visibly incorrect.
 *
 * Elements in play (and why each is here, not contrived):
 * - AjaxPopUpButton (wonder-select)   product picker on each line + the customer picker
 * - AjaxBrowser (multi-select)        invoice tags
 * - AjaxObserveField                  qty / unit-price fields recompute the line + grand total live
 * - AjaxUpdateLink + multi-update     "add line" / "remove line" refresh the rows AND the totals in one request
 * - AjaxSubmitButton                  Save
 * - AjaxDefaultSubmitButton           Enter-to-save
 * - AjaxUpdateTrigger                 toggling "finalised" cascades a refresh to the summary panel
 * - AjaxSelfUpdatingContainer         a "last activity" ticker polling in the background
 * - AjaxPing / AjaxPingUpdate         a concurrent-edit watcher (cache-key gated)
 * - AjaxBusySpinner                   activity indicator during saves
 * - AjaxModalContainer                "edit customer details" dialog with its own form
 */
public class ScenarioInvoice extends PlaygroundPage {

	private final List<InvoiceLine> _lines;
	private int _nextLineID;

	/** Bindings the widgets edit. Public ivars per the playground idiom. */
	public InvoiceLine currentLine;        // repetition item
	public String currentProduct;          // popup repetition item
	public String currentTag;              // browser repetition item
	public String customer;
	public NSArray<String> tags;
	public boolean finalised;

	private int _saveCount;
	private int _activityTick;

	public ScenarioInvoice(WOContext context) {
		super(context);
		_lines = new ArrayList<>();
		_lines.add(new InvoiceLine(1, "Widget", 2, 500));
		_lines.add(new InvoiceLine(2, "Gadget", 1, 1200));
		_lines.add(new InvoiceLine(3, "Sprocket", 4, 75));
		_nextLineID = 4;
		customer = "Acme Corp";
		tags = new NSArray<>();
	}

	// --- data the widgets bind to ---------------------------------------------------------------

	public List<InvoiceLine> lines() {
		return _lines;
	}

	public List<String> products() {
		return Arrays.asList("Widget", "Gadget", "Sprocket", "Cog", "Flange", "Grommet", "Bracket", "Bearing");
	}

	public List<String> customers() {
		return Arrays.asList("Acme Corp", "Globex", "Initech", "Umbrella", "Wayne Enterprises", "Stark Industries");
	}

	public List<String> allTags() {
		return Arrays.asList("urgent", "wholesale", "tax-exempt", "recurring", "international");
	}

	/** A stable per-row id used for DOM ids - never reused, so data follows identity across morphs. */
	public String rowID() {
		return "line-" + currentLine.id();
	}

	public String qtyFieldID() {
		return "qty-" + currentLine.id();
	}

	public String priceFieldID() {
		return "price-" + currentLine.id();
	}

	public String lineContainerID() {
		return "linebox-" + currentLine.id();
	}

	public String productFieldID() {
		return "product-" + currentLine.id();
	}

	/** This line's own box, the totals panel, and the rendered invoice - a PER-ROW targeted update.
	 *  This is deliberately the cache-hungry pattern: editing one row re-renders only that row, so the
	 *  OTHER rows' action links (move/remove) keep an ever-older WO context id. Under the legacy flat,
	 *  context-id-keyed page-replacement cache that eventually evicts a still-valid container's page and
	 *  the stale link 500s. This page is the stress test for the container-identity-keyed cache on this
	 *  branch: if the cache is correct, per-row updates work indefinitely. (master uses a whole-container
	 *  refresh as the pragmatic workaround for the old cache.) */
	public String lineAndTotalsIDs() {
		return "linebox-" + currentLine.id() + ";totalsPanel;renderedInvoice";
	}

	public boolean currentLineInvalid() {
		return !currentLine.isValid();
	}

	public String lineRowClass() {
		return currentLine.isValid() ? "" : "invalid";
	}

	public String statusPillClass() {
		return finalised ? "pill final" : "pill";
	}

	// --- derived totals (the canary) ------------------------------------------------------------

	public int grandTotal() {
		int sum = 0;
		for (InvoiceLine line : _lines) {
			sum += line.lineTotal();
		}
		return sum;
	}

	public int lineCount() {
		return _lines.size();
	}

	public boolean hasInvalidLine() {
		for (InvoiceLine line : _lines) {
			if (!line.isValid()) {
				return true;
			}
		}
		return false;
	}

	public int saveCount() {
		return _saveCount;
	}

	/** Just the totals container - the target an observe-field on a qty/price field refreshes. */
	public String totalsContainerID() {
		return "totalsPanel";
	}

	// --- actions --------------------------------------------------------------------------------

	/** A qty/price field changed - nothing to do server-side beyond letting the binding round-trip
	 *  and the totals re-render. */
	public WOActionResults lineEdited() {
		return null;
	}

	public WOActionResults addLine() {
		_lines.add(new InvoiceLine(_nextLineID++, "Widget", 1, 100));
		return null;
	}

	/** Remove the row currently being rendered in the repetition (bound via currentLine). */
	public WOActionResults removeCurrentLine() {
		_lines.remove(currentLine);
		return null;
	}

	public boolean isFirstLine() {
		return _lines.indexOf(currentLine) == 0;
	}

	public boolean isLastLine() {
		return _lines.indexOf(currentLine) == _lines.size() - 1;
	}

	public boolean canMoveUp() {
		return !isFirstLine();
	}

	public boolean canMoveDown() {
		return !isLastLine();
	}

	/** Swap the current line with the one above it. */
	public WOActionResults moveCurrentLineUp() {
		int i = _lines.indexOf(currentLine);
		if (i > 0) {
			_lines.set(i, _lines.set(i - 1, currentLine));
		}
		return null;
	}

	/** Swap the current line with the one below it. */
	public WOActionResults moveCurrentLineDown() {
		int i = _lines.indexOf(currentLine);
		if (i >= 0 && i < _lines.size() - 1) {
			_lines.set(i, _lines.set(i + 1, currentLine));
		}
		return null;
	}

	/**
	 * Drag-to-reorder target for AjaxSortable. The element reports the move purely positionally; we read
	 * it with AjaxSortable.dragResult and apply it the same way the move-up/down actions do, just
	 * generalized to an arbitrary distance: remove from fromIndex, insert at toIndex. Both indices refer
	 * to the same (pre-move) list snapshot the user was looking at.
	 */
	public WOActionResults reorderLines() {
		AjaxSortable.DragResult move = AjaxSortable.dragResult(context().request());
		if (move != null && move.isValidFor(_lines.size())) {
			_lines.add(move.toIndex(), _lines.remove(move.fromIndex()));
		}
		return null;
	}

	public WOActionResults save() {
		_saveCount++;
		return null;
	}

	public WOActionResults toggleFinalised() {
		finalised = !finalised;
		return null;
	}

	// --- background pollers ---------------------------------------------------------------------

	/** Drives the "last activity" ticker (AjaxSelfUpdatingContainer): bumps each poll so the ticker
	 *  visibly advances WITHOUT disturbing the editing happening elsewhere on the page. */
	public WOActionResults activityPoll() {
		_activityTick++;
		return null;
	}

	public int activityTick() {
		return _activityTick;
	}

	/** The server's current time, re-read on every render - so each periodic refresh of the ticker
	 *  visibly shows a NEW value (proving a real round-trip happened, not just a busy flash). */
	public String serverTime() {
		return LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
	}

	/** The cache key for the concurrent-edit AjaxPing: derived from the invoice state, so the heavy
	 *  target only refreshes when something actually changed. */
	public String invoiceCacheKey() {
		return _lines.size() + ":" + grandTotal() + ":" + _saveCount + ":" + finalised;
	}

	public String finalisedLabel() {
		return finalised ? "Finalised" : "Draft";
	}

	public boolean hasTags() {
		return tags != null && tags.count() > 0;
	}

	/** The selected tags as a comma-separated string for the rendered invoice header. */
	public String tagsDisplay() {
		return tags == null ? "" : tags.componentsJoinedByString(", ");
	}
}
