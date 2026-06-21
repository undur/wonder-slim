package ajaxplayground.components;

import java.util.ArrayList;
import java.util.List;

import com.webobjects.appserver.WOContext;

import ajaxplayground.apiext.ApiextElement;

/**
 * Renders the AjaxSlim element reference ENTIRELY from the {@code .apiext} files - the prototype of the
 * end-state where the hand-written reference page is replaced by a template that loops over these files.
 * Loads every element via the resource manager, in reference order.
 */
public class ApiextRenderTest extends PlaygroundPage {

	/** The 14 AjaxSlim elements, in the order the hand-written reference presents them. */
	private static final String[] ELEMENT_NAMES = {
		"AjaxUpdateContainer", "AjaxSelfUpdatingContainer", "AjaxUpdateLink", "AjaxSubmitButton",
		"AjaxDefaultSubmitButton", "AjaxObserveField", "AjaxUpdateTrigger", "AjaxPopUpButton",
		"AjaxBrowser", "AjaxModalContainer", "AjaxBusySpinner", "AjaxPing", "AjaxPingUpdate", "AjaxSortable"
	};

	private ApiextElement currentElement;
	private ApiextElement.Binding currentBinding;
	private ApiextElement.Validation currentValidation;
	private String currentTag;

	public ApiextRenderTest( WOContext context ) {
		super( context );
	}

	/** Load every element's parsed model (skipping any that fail to load - so a bad file is visible, not fatal). */
	public List<ApiextElement> elements() {
		List<ApiextElement> out = new ArrayList<>();
		for ( String name : ELEMENT_NAMES ) {
			ApiextElement el = ApiextElement.load( name, "AjaxSlim" );
			if ( el != null ) {
				out.add( el );
			}
		}
		return out;
	}

	public ApiextElement currentElement() { return currentElement; }
	public void setCurrentElement( ApiextElement value ) { currentElement = value; }

	public ApiextElement.Binding currentBinding() { return currentBinding; }
	public void setCurrentBinding( ApiextElement.Binding value ) { currentBinding = value; }

	public ApiextElement.Validation currentValidation() { return currentValidation; }
	public void setCurrentValidation( ApiextElement.Validation value ) { currentValidation = value; }

	public String currentTag() { return currentTag; }
	public void setCurrentTag( String value ) { currentTag = value; }

	public boolean currentBindingRequired() {
		return currentBinding != null && currentBinding.required;
	}

	// --- tag -> badge mapping (framework-specific presentation of the portable tag value) ------------

	/** The CSS badge class for the current tag (e.g. "update" -> "t-update"). */
	public String currentTagBadgeClass() {
		return "tag t-" + ( "trigger".equals( currentTag ) ? "trigger" : currentTag );
	}

	/** The badge label for the current tag (e.g. "update" -> "Update", "trigger" -> "Activity"). */
	public String currentTagBadgeLabel() {
		switch ( currentTag == null ? "" : currentTag ) {
			case "update":  return "Update";
			case "widget":  return "Widget";
			case "server":  return "Server";
			case "trigger": return "Activity";
			default:        return currentTag;
		}
	}
}
