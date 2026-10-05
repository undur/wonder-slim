package ajaxplayground.components.scenario;

/**
 * One editable line on the integration invoice. A plain mutable bean - the page edits these through
 * wonder-select (product) and observe-field-driven text fields (quantity / unit price), and the line
 * total is derived so it updates live as the inputs change.
 *
 * Identity matters here: lines carry a stable {@link #id} that never changes as rows are added or
 * removed, so a row's data must follow its identity across a morph (the row-identity hazard) - if an
 * observer mis-fires or a morph drops a row, the WRONG line total moves, which is immediately visible.
 */
public class InvoiceLine {

	private final int _id;
	private String _product;
	private int _quantity;
	private int _unitPrice;

	public InvoiceLine(int id, String product, int quantity, int unitPrice) {
		_id = id;
		_product = product;
		_quantity = quantity;
		_unitPrice = unitPrice;
	}

	public int id() {
		return _id;
	}

	public String product() {
		return _product;
	}

	public void setProduct(String product) {
		_product = product;
	}

	public int quantity() {
		return _quantity;
	}

	public void setQuantity(int quantity) {
		_quantity = quantity;
	}

	public int unitPrice() {
		return _unitPrice;
	}

	public void setUnitPrice(int unitPrice) {
		_unitPrice = unitPrice;
	}

	// String-typed accessors for the text-field bindings. A WOTextField's value round-trips as a String,
	// and WO key-value coding does NOT coerce a String into a primitive-int setter (it throws
	// IllegalArgumentException on the reflective set). Binding through these String accessors - which
	// parse leniently and ignore garbage - keeps the bean integer-typed while accepting form input.
	public String quantityField() {
		return String.valueOf(_quantity);
	}

	public void setQuantityField(String value) {
		_quantity = parseIntOrZero(value);
	}

	public String unitPriceField() {
		return String.valueOf(_unitPrice);
	}

	public void setUnitPriceField(String value) {
		_unitPrice = parseIntOrZero(value);
	}

	private static int parseIntOrZero(String value) {
		if (value == null) {
			return 0;
		}
		try {
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e) {
			return 0;
		}
	}

	/** The derived line total - quantity * unit price. Recomputed on every render, so a stale value
	 *  here would mean a binding didn't round-trip. */
	public int lineTotal() {
		return _quantity * _unitPrice;
	}

	/** A line is invalid if its quantity is non-positive - surfaced inline so a validation error has
	 *  to survive a morph to stay visible. */
	public boolean isValid() {
		return _quantity > 0 && _unitPrice >= 0;
	}
}
