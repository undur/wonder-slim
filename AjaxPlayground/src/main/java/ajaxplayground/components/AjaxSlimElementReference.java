package ajaxplayground.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.webobjects.appserver.WOContext;

import ajaxplayground.apiext.ApiextElement;

/**
 * The AjaxSlim element reference, rendered ENTIRELY from the {@code .apiext} files - no hand-written
 * element docs. Loads every element via the resource manager, in reference order, and renders each
 * section (role, bindings table, validations) from its parsed model.
 * <p>
 * The category BADGES (Update/Widget/Server/Activity) are AjaxSlim's own editorial taxonomy, NOT part of
 * the element-API contract, so they live here rather than in the {@code .apiext} files: this component
 * owns the element -> tag mapping (below) and the tag -> badge presentation. See
 * {@code docs/APIEXT_FORMAT.md} for why the format itself carries no tags.
 */
public class AjaxSlimElementReference extends PlaygroundPage {

	/**
	 * The 14 AjaxSlim elements in reference order, each mapped to its AjaxSlim category tag(s). This is
	 * framework-specific editorial categorization, deliberately kept out of the .apiext format.
	 */
	private static final Map<String, List<String>> ELEMENT_TAGS = new LinkedHashMap<>();
	static {
		ELEMENT_TAGS.put( "AjaxUpdateContainer", List.of( "update" ) );
		ELEMENT_TAGS.put( "AjaxSelfUpdatingContainer", List.of( "update" ) );
		ELEMENT_TAGS.put( "AjaxUpdateLink", List.of( "update" ) );
		ELEMENT_TAGS.put( "AjaxSubmitButton", List.of( "update" ) );
		ELEMENT_TAGS.put( "AjaxDefaultSubmitButton", List.of( "update" ) );
		ELEMENT_TAGS.put( "AjaxObserveField", List.of( "update" ) );
		ELEMENT_TAGS.put( "AjaxUpdateTrigger", List.of( "server" ) );
		ELEMENT_TAGS.put( "AjaxPopUpButton", List.of( "widget" ) );
		ELEMENT_TAGS.put( "AjaxBrowser", List.of( "widget" ) );
		ELEMENT_TAGS.put( "AjaxModalContainer", List.of( "update" ) );
		ELEMENT_TAGS.put( "AjaxBusySpinner", List.of( "trigger" ) );
		ELEMENT_TAGS.put( "AjaxPing", List.of( "trigger" ) );
		ELEMENT_TAGS.put( "AjaxPingUpdate", List.of( "server" ) );
		ELEMENT_TAGS.put( "AjaxSortable", List.of() );
	}

	private ApiextElement currentElement;
	private ApiextElement.Binding currentBinding;
	private ApiextElement.Validation currentValidation;
	private String currentTag;

	public AjaxSlimElementReference( WOContext context ) {
		super( context );
	}

	/** Load every element's parsed model (skipping any that fail to load - so a bad file is visible, not fatal). */
	public List<ApiextElement> elements() {
		List<ApiextElement> out = new ArrayList<>();
		for ( String name : ELEMENT_TAGS.keySet() ) {
			ApiextElement el = ApiextElement.load( name, "AjaxSlim" );
			if ( el != null ) {
				out.add( el );
			}
		}
		return out;
	}

	public ApiextElement currentElement() { return currentElement; }
	public void setCurrentElement( ApiextElement value ) { currentElement = value; }

	/** This element's AjaxSlim category tags - from this component's editorial map, NOT the .apiext file. */
	public List<String> currentElementTags() {
		return currentElement == null ? List.of() : ELEMENT_TAGS.getOrDefault( currentElement.className(), List.of() );
	}

	/** The TOC anchor href for the current element, e.g. "#AjaxUpdateContainer". */
	public String currentElementAnchor() {
		return currentElement == null ? "#" : "#" + currentElement.className();
	}

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
