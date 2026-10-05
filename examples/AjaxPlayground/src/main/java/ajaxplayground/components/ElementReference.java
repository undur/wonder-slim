package ajaxplayground.components;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSBundle;

import ajaxplayground.apiext.ApiextElement;

/**
 * An element reference, rendered ENTIRELY from {@code .apiext} files - no hand-written element docs. One
 * page for every library: each {@link Source} says where its elements come from, and the page renders each
 * element's section (role, bindings table, constraints) from its parsed model.
 * <p>
 * The category BADGES (Update/Widget/Server/Activity) are AjaxSlim's own editorial taxonomy, NOT part of
 * the element-API contract, so they live here rather than in the {@code .apiext} files: this component
 * owns the element -> tag mapping (below) and the tag -> badge presentation. See
 * the apiext-format spec (github.com/undur/apiext-format) for why the format itself carries no tags.
 */
public class ElementReference extends PlaygroundPage {

	/**
	 * The 14 AjaxSlim elements in reference order, each mapped to its AjaxSlim category tag(s). This is
	 * framework-specific editorial categorization, deliberately kept out of the .apiext format.
	 */
	private static final Map<String, List<String>> AJAXSLIM_TAGS = new LinkedHashMap<>();
	static {
		AJAXSLIM_TAGS.put( "AjaxUpdateContainer", List.of( "update" ) );
		AJAXSLIM_TAGS.put( "AjaxSelfUpdatingContainer", List.of( "update" ) );
		AJAXSLIM_TAGS.put( "AjaxUpdateLink", List.of( "update" ) );
		AJAXSLIM_TAGS.put( "AjaxSubmitButton", List.of( "update" ) );
		AJAXSLIM_TAGS.put( "AjaxDefaultSubmitButton", List.of( "update" ) );
		AJAXSLIM_TAGS.put( "AjaxObserveField", List.of( "update" ) );
		AJAXSLIM_TAGS.put( "AjaxUpdateTrigger", List.of( "server" ) );
		AJAXSLIM_TAGS.put( "AjaxPopUpButton", List.of( "widget" ) );
		AJAXSLIM_TAGS.put( "AjaxBrowser", List.of( "widget" ) );
		AJAXSLIM_TAGS.put( "AjaxModalContainer", List.of( "update" ) );
		AJAXSLIM_TAGS.put( "AjaxBusySpinner", List.of( "trigger" ) );
		AJAXSLIM_TAGS.put( "AjaxPing", List.of( "trigger" ) );
		AJAXSLIM_TAGS.put( "AjaxPingUpdate", List.of( "server" ) );
		AJAXSLIM_TAGS.put( "AjaxSortable", List.of() );
	}

	/**
	 * A library the page documents, and where its {@code .apiext} files come from
	 */
	public enum Source {

		AjaxSlim(
				"/element-reference",
				"AjaxSlim",
				"Every element in the AjaxSlim library: what it does and its full set of bindings. For migration notes and the bigger picture, see the <a href=\"/reference\">guide</a>.",
				ElementReference::ajaxSlimElements ),

		WonderSlim(
				"/element-reference/wonder-slim",
				"wonder-slim",
				"The elements of wonder-slim's ERExtensions: its additions, and its patches and replacements of WebObjects' own elements. A replacement answers to the WebObjects element's name in templates (<code>ERXWOString</code> is what <code>WOString</code> renders).",
				() -> ApiextElement.loadAll( NSBundle.bundleForName( "ERExtensions" ), null ) ),

		WebObjects(
				"/element-reference/webobjects",
				"WebObjects",
				"WebObjects' own dynamic elements, as of WebObjects 5.4.3. Several are patched or replaced in a wonder-slim application; see the <a href=\"/element-reference/wonder-slim\">wonder-slim reference</a> for those. The files are the ones Parslips' component editor uses.",
				() -> ApiextElement.loadAll( NSBundle.mainBundle(), "apiext/webobjects" ) );

		private final String _path;
		private final String _title;
		private final String _leadHtml;
		private final Supplier<List<ApiextElement>> _elements;

		Source( String path, String title, String leadHtml, Supplier<List<ApiextElement>> elements ) {
			_path = path;
			_title = title;
			_leadHtml = leadHtml;
			_elements = elements;
		}

		public String path() { return _path; }
		public String title() { return _title; }
		public String leadHtml() { return _leadHtml; }
	}

	public Source source = Source.AjaxSlim;
	public Source currentSource;

	private List<ApiextElement> _elements;
	private ApiextElement currentElement;
	private ApiextElement.Binding currentBinding;
	private ApiextElement.Constraint currentConstraint;
	private String currentTag;

	public ElementReference( WOContext context ) {
		super( context );
	}

	/**
	 * @return The reference page for the given source
	 */
	public static ElementReference page( WOContext context, Source source ) {
		final ElementReference page = (ElementReference)WOApplication.application().pageWithName( ElementReference.class.getName(), context );
		page.source = source;
		return page;
	}

	/** The AjaxSlim elements, in reference order (skipping any that fail to load - so a bad file is visible, not fatal). */
	private static List<ApiextElement> ajaxSlimElements() {
		List<ApiextElement> out = new ArrayList<>();
		for ( String name : AJAXSLIM_TAGS.keySet() ) {
			ApiextElement el = ApiextElement.load( name, "AjaxSlim" );
			if ( el != null ) {
				out.add( el );
			}
		}
		return out;
	}

	public List<ApiextElement> elements() {
		if ( _elements == null ) {
			_elements = elementsFor( source );
		}
		return _elements;
	}

	/** The elements of a source, in reference order - shared by this page and the JSON endpoint. */
	public static List<ApiextElement> elementsFor( Source source ) {
		return source._elements.get();
	}

	/** A source's editorial category tags for an element (only AjaxSlim has any) - shared with the JSON endpoint. */
	public static List<String> tagsFor( Source source, ApiextElement element ) {
		return source == Source.AjaxSlim ? AJAXSLIM_TAGS.getOrDefault( element.className(), List.of() ) : List.of();
	}

	public List<Source> sources() {
		return List.of( Source.values() );
	}

	public String currentSourceClass() {
		return currentSource == source ? "current" : null;
	}

	/** Only AjaxSlim has category tags, so only its page shows their legend */
	public boolean hasCategories() {
		return source == Source.AjaxSlim;
	}

	public ApiextElement currentElement() { return currentElement; }
	public void setCurrentElement( ApiextElement value ) { currentElement = value; }

	/** This element's AjaxSlim category tags - from this component's editorial map, NOT the .apiext file. */
	public List<String> currentElementTags() {
		return currentElement == null || !hasCategories() ? List.of() : AJAXSLIM_TAGS.getOrDefault( currentElement.className(), List.of() );
	}

	/** The TOC anchor href for the current element, e.g. "#AjaxUpdateContainer". */
	public String currentElementAnchor() {
		return currentElement == null ? "#" : "#" + currentElement.className();
	}

	public ApiextElement.Binding currentBinding() { return currentBinding; }
	public void setCurrentBinding( ApiextElement.Binding value ) { currentBinding = value; }

	public ApiextElement.Constraint currentConstraint() { return currentConstraint; }
	public void setCurrentConstraint( ApiextElement.Constraint value ) { currentConstraint = value; }

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
