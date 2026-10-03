package er.extensions.appserver;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver._private.WOKeyValueAssociation;

import ng.appserver.templating.parser.NGDeclaration.NGBindingValue;
import parsley.ParsleyDefaultAssociationFactory;

/**
 * Parsley's associations, with key path bindings that reach static members ({@link ERXKeyValueAssociation}, #172)
 */

public class ERXAssociationFactory extends ParsleyDefaultAssociationFactory {

	@Override
	public WOAssociation associationForBindingValue( final String name, final NGBindingValue value, final boolean isInline ) {
		final WOAssociation association = super.associationForBindingValue( name, value, isInline );
		return association != null && association.getClass() == WOKeyValueAssociation.class ? new ERXKeyValueAssociation( association.keyPath() ) : association;
	}
}
