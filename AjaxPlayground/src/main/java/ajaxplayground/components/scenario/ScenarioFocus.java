package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: focus / input-state preservation.
 *
 * A form lives inside an AjaxUpdateContainer that refreshes (via AjaxObserveField) whenever a field
 * changes. With morph on, the cursor / caret / scroll position should survive the refresh; with
 * morph off (classic innerHTML replacement) focus is lost on every keystroke-triggered update.
 *
 * The "computed" field is derived server-side from the input so each update genuinely re-renders
 * the container with new content - exercising a real morph, not a no-op.
 */
public class ScenarioFocus extends PlaygroundPage {

	private String _firstName;
	private String _lastName;

	/** Whether the container morphs. Bound to a toggle so morph vs classic can be compared live. */
	public boolean morph = true;

	public ScenarioFocus( WOContext context ) {
		super( context );
	}

	public String firstName() {
		return _firstName;
	}

	public void setFirstName( String value ) {
		_firstName = value;
	}

	public String lastName() {
		return _lastName;
	}

	public void setLastName( String value ) {
		_lastName = value;
	}

	/** A server-computed value so each refresh re-renders the container with changed content. */
	public String computedGreeting() {
		String first = _firstName == null ? "" : _firstName.trim();
		String last = _lastName == null ? "" : _lastName.trim();
		String full = (first + " " + last).trim();
		if( full.isEmpty() ) {
			return "(type something above)";
		}
		return "Hello, " + full + "! That is " + full.replaceAll( "\\s+", "" ).length() + " non-space characters.";
	}

	public boolean morph() {
		return morph;
	}

	public void setMorph( boolean value ) {
		morph = value;
	}
}
