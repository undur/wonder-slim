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
 * Embeds an already constructed component instance in a template.
 *
 * <pre>
 * &lt;wo:ERXWOComponentInstance instance="$inspector" selectedObject="$object" /&gt;
 * </pre>
 *
 * WO's own way of embedding a component (WOComponentReference, what &lt;wo:MyComponent&gt; compiles to) constructs the
 * instance itself, by name, on first render. That leaves bindings as the only way to initialize it, and no handle to
 * call methods on. This element is WOComponentReference with the construction step handed to you: the parent
 * constructs the instance (see {@link ERXComponentUtilities#instantiate(Class, WOContext)}), configures it, keeps a
 * reference to it, and binds it to [instance]. Everything else - bindings passed alongside, wrapped content, the
 * per-phase push/pull, awake/sleep, page caching - works exactly as for a normally embedded component. The
 * per-phase dance below mirrors WOComponentReference._pushComponentInContext / _popComponentFromContext.
 *
 * <h3>Why registering the instance as a subcomponent matters</h3>
 * Once adopted, the instance is registered with its parent under this element's element ID, exactly as
 * WOComponentReference does. That registration is what keeps it alive and correct across requests: WOComponent's
 * _awakeInContext and _sleepInContext walk the subcomponent tree, so a page restored from the page cache awakens the
 * instance with the new request's context and puts it to sleep afterwards, without this element doing anything.
 *
 * <h3>Replacing the instance</h3>
 * The [instance] binding is evaluated on every phase, and a different instance is adopted the moment it appears -
 * so a parent can swap the embedded component at will (typically from an action; the response phase then renders
 * the replacement). Adopting overwrites the parent's subcomponent slot, dropping the old instance from the tree
 * altogether. That is deliberately cleaner than WOSwitchComponent, whose previously shown components linger in the
 * parent forever, awakened and slept on every request.
 *
 * <h3>Rules, enforced loudly</h3>
 * <ul>
 * <li>[instance] must not be null. There is nothing sensible to render for "no component", and a silent blank hides
 * the bug.</li>
 * <li>Stateless components are refused. WOComponentDefinition.componentInstanceInContext() hands out a shared, pooled
 * instance for a stateless definition; holding one as a registered subcomponent breaks the pool's contract.</li>
 * <li>An instance already embedded in a <em>different</em> parent is refused. _setParent would silently re-parent
 * it, and each parent would then own the same object in its subcomponent tree. (Binding the same instance into two
 * slots of the <em>same</em> parent - e.g. twice inside a repetition - is not detected; don't.)</li>
 * </ul>
 *
 * <h3>Constructing the instance</h3>
 * Use {@link ERXComponentUtilities#instantiate(Class, WOContext)}, not pageWithName(): pageWithName awakens the
 * instance and flags it as a page, and this element awakens it again on adoption - a double awake() in the same
 * request. instantiate() constructs without awakening, which is the state a freshly constructed subcomponent is in
 * when WOComponentReference adopts it. (ensureAwakeInContext() would not help here: it compares context identity and
 * would skip the awake for an instance constructed in the current request's context.)
 */
public class ERXWOComponentInstance extends WODynamicElement {

	/**
	 * Name of the binding holding the component instance
	 */
	private static final String INSTANCE_BINDING = "instance";

	/**
	 * For obtaining the bound component instance
	 */
	private final WOAssociation _instanceAssociation;

	/**
	 * Bindings on the element other than [instance] - passed on to the embedded instance as its bindings
	 */
	private final NSMutableDictionary<String, WOAssociation> _associations;

	/**
	 * Content wrapped by the element in the template - becomes the instance's child template (rendered by its wo:content)
	 */
	private final WOElement _wrappedContent;

	public ERXWOComponentInstance( String name, NSDictionary<String, WOAssociation> associations, WOElement wrappedContent ) {
		super( null, null, null );
		_wrappedContent = wrappedContent;

		_associations = associations.mutableClone();
		_instanceAssociation = _associations.remove( INSTANCE_BINDING );

		if( _instanceAssociation == null ) {
			throw new WODynamicElementCreationException( "<%s> : [%s] is a required binding".formatted( getClass().getSimpleName(), INSTANCE_BINDING ) );
		}
	}

	/**
	 * Invoked before each R-R phase: resolves the bound instance, adopts it if it isn't the one registered with the
	 * parent for this element (first render, or the parent replaced it), then makes it the current component.
	 */
	private void pushComponent( WOContext context ) {
		final WOComponent parent = context.component();
		final String elementID = context.elementID();

		final WOComponent instance = boundInstance( parent );
		final WOComponent registered = parent._subcomponentForElementWithID( elementID );

		if( registered != instance ) {
			adopt( instance, parent, elementID, context );
		}

		// Identity-checked no-op after adoption. Done on every phase regardless, mirroring WOComponentReference.
		instance._setParent( parent, _associations, _wrappedContent );
		instance.pullValuesFromParent();
		context._setCurrentComponent( instance );
	}

	/**
	 * Invoked after each R-R phase
	 */
	private void popComponent( WOContext context ) {
		final WOComponent instance = context.component();
		instance.pushValuesToParent();
		context._setCurrentComponent( instance.parent() );
	}

	/**
	 * @return The instance currently bound to [instance], validated
	 */
	private WOComponent boundInstance( WOComponent parent ) {
		final Object bound = _instanceAssociation.valueInComponent( parent );

		if( bound == null ) {
			throw new IllegalStateException( "<%s> : [%s] evaluated to null in %s. Bind a constructed component instance (see ERXComponentUtilities.instantiate())".formatted( getClass().getSimpleName(), INSTANCE_BINDING, parent.name() ) );
		}

		if( !(bound instanceof WOComponent instance) ) {
			throw new IllegalStateException( "<%s> : [%s] must be a WOComponent instance, but evaluated to a %s in %s".formatted( getClass().getSimpleName(), INSTANCE_BINDING, bound.getClass().getName(), parent.name() ) );
		}

		return instance;
	}

	/**
	 * Registers the instance as the parent's subcomponent for this element and awakens it - what
	 * WOComponentReference does for an instance it has just constructed.
	 */
	private void adopt( WOComponent instance, WOComponent parent, String elementID, WOContext context ) {

		if( instance.isStateless() ) {
			throw new IllegalStateException( "<%s> : %s is stateless. Stateless component instances are pooled and shared, so one can't be held as an embedded instance. Embed it by name instead, or make it stateful".formatted( getClass().getSimpleName(), instance.name() ) );
		}

		final WOComponent currentParent = instance.parent();

		if( currentParent != null && currentParent != parent ) {
			throw new IllegalStateException( "<%s> : The %s instance bound in %s is already embedded in %s. A component instance can be embedded in one place only".formatted( getClass().getSimpleName(), instance.name(), parent.name(), currentParent.name() ) );
		}

		instance._setParent( parent, _associations, _wrappedContent );
		parent._setSubcomponent( instance, elementID );
		instance._awakeInContext( context );
	}

	@Override
	public void takeValuesFromRequest( WORequest request, WOContext context ) {
		pushComponent( context );
		context.component().takeValuesFromRequest( request, context );
		popComponent( context );
	}

	@Override
	public WOActionResults invokeAction( WORequest request, WOContext context ) {
		pushComponent( context );
		final WOActionResults result = context.component().invokeAction( request, context );
		popComponent( context );
		return result;
	}

	@Override
	public void appendToResponse( WOResponse response, WOContext context ) {
		pushComponent( context );
		context.component().appendToResponse( response, context );
		popComponent( context );
	}
}
