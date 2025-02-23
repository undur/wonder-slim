package er.extensions.components;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WODynamicElement;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WODynamicElementCreationException;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;

/**
 * An element for placing an already constructed component instance into a template, similar to SwitchComponent 
 * 
 * Component instances can be constructed using:
 * 
 * application()._componentDefinition( yourComponentName, context()._languages() ).componentInstanceInContext( context() )
 * 
 * FIXME: This is work in progress and still being tested // Hugi 2025-02-23
 * TODO: Allow replacing the component instance? We currently insert the instance when the element is first rendered, then don't touch it again // Hugi 2025-02-23
 */

public class ERXSwitchComponentInstance extends WODynamicElement {

	/**
	 * Bindings on the element other than [componentInstance]
	 */
	private final NSMutableDictionary<String, WOAssociation> _associations;

	/**
	 * Content wrapped by the element in the template
	 */
	private final WOElement _wrappedContent;

	/**
	 * For obtaining the bound component instance
	 */
	private final WOAssociation _componentInstanceAssociation;

	public ERXSwitchComponentInstance( String name, NSDictionary<String, WOAssociation> associations, WOElement wrappedContent ) {
		super( null, null, null );
		_wrappedContent = wrappedContent;

		_associations = associations.mutableClone();
		_componentInstanceAssociation = _associations.remove( "componentInstance" );

		if( _componentInstanceAssociation == null ) {
			throw new WODynamicElementCreationException( "[componentInstance] is a requried binding" );
		}
	}

	/**
	 * Invoked before each R-R phase
	 */
	private void beforeComponent( WOContext context ) {
		final WOComponent currentComponent = context.component();

		// Check if our component instance has been registered with the current component as a subcomponent
		WOComponent componentInstance = currentComponent._subcomponentForElementWithID( context.elementID() );

		// If we haven't registered our component with the current component, set it to our given instance
		if( componentInstance == null ) {
			componentInstance = (WOComponent)_componentInstanceAssociation.valueInComponent( currentComponent );
			componentInstance._setParent( currentComponent, _associations, _wrappedContent );
			currentComponent._setSubcomponent( componentInstance, context.elementID() );
			componentInstance._awakeInContext( context ); 
		}

		componentInstance.pullValuesFromParent();
		context._setCurrentComponent( componentInstance );
	}

	/**
	 * Invoked after each R-R phase
	 */
	private void afterComponent( WOContext context ) {
		context.component().pushValuesToParent();
		context._setCurrentComponent( context.component().parent() );
	}

	@Override
	public void takeValuesFromRequest( WORequest request, WOContext context ) {
		beforeComponent( context );
		context.component().takeValuesFromRequest( request, context );
		afterComponent( context );
	}

	@Override
	public WOActionResults invokeAction( WORequest request, WOContext context ) {
		beforeComponent( context );
		final WOActionResults returnedActionValue = context.component().invokeAction( request, context );
		afterComponent( context );
		return returnedActionValue;
	}

	@Override
	public void appendToResponse( WOResponse response, WOContext context ) {
		beforeComponent( context );
		context.component().appendToResponse( response, context );
		afterComponent( context );
	}
}