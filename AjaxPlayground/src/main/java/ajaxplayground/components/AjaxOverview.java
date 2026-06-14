package ajaxplayground.components;

import com.webobjects.appserver.WOContext;

/**
 * A neutral reference catalogue of every element in the Ajax framework, grouped by role, with a
 * maturity badge per element. The page content is generated from a full analysis of the framework;
 * the opinionated keep/refactor/delete assessment that accompanies it lives in Ajax/docs/ASSESSMENT.md.
 */
public class AjaxOverview extends PlaygroundPage {

	public AjaxOverview( WOContext context ) {
		super( context );
	}
}
