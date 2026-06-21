package ajaxplayground.components;

import java.util.List;

import com.webobjects.appserver.WOContext;

import ajaxplayground.apiext.ApiextElement;

/**
 * Proof-of-concept: render an element's reference-page section ENTIRELY from its {@code .apiext} file,
 * instead of from hand-written HTML. If this matches the hand-written AjaxUpdateContainer section on
 * the real reference page, the format captures everything and the reference page can become a template
 * that loops over the {@code .apiext} files.
 */
public class ApiextRenderTest extends PlaygroundPage {

	private ApiextElement.Binding currentBinding;
	private String currentTag;

	public ApiextRenderTest( WOContext context ) {
		super( context );
	}

	public String currentTag() { return currentTag; }
	public void setCurrentTag( String value ) { currentTag = value; }
	public boolean currentTagIsUpdate() { return "update".equals( currentTag ); }

	/** The element under test - read from AjaxSlim's components folder via the resource manager. */
	public ApiextElement element() {
		return ApiextElement.load( "AjaxUpdateContainer", "AjaxSlim" );
	}

	public List<ApiextElement.Binding> bindings() {
		return element().bindings();
	}

	public ApiextElement.Binding currentBinding() {
		return currentBinding;
	}

	public void setCurrentBinding( ApiextElement.Binding value ) {
		currentBinding = value;
	}

	/** A binding's required marker, rendered as the same red bullet the reference uses. */
	public boolean currentBindingRequired() {
		return currentBinding != null && currentBinding.required;
	}
}
