package ajaxplayground.components.scenario;

import com.webobjects.appserver.WOContext;

import ajaxplayground.components.PlaygroundPage;

/**
 * Scenario: partial submits of fields whose name is bound explicitly ({@code name="email"}), next to ones that aren't.
 *
 * A partial submit sends only the changed field, and tells the server which one it was; the server then takes values
 * only for that element. A field with an explicit name used to be told apart by its name while the server compared
 * element IDs, so its own value was dropped.
 */
public class ScenarioNamedField extends PlaygroundPage {

	public String email = "";
	public String nickname = "";
	public String comment = "";
	public String color = "red";
	public java.util.List<String> colors = java.util.List.of( "red", "green", "blue" );
	public String currentColor;

	public ScenarioNamedField( WOContext context ) {
		super( context );
	}
}
