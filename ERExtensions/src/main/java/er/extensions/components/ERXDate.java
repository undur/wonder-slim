package er.extensions.components;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WODynamicElement;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WODynamicElementCreationException;
import com.webobjects.foundation.NSDictionary;

/**
 * For display of LocalDate and LocaldDateTime
 */

public class ERXDate extends WODynamicElement {

	// FIXME: We're hardcoding the Icelandic date formats here. This should really be configurable/locale based // Hugi 2025-06-03
	private static final DateTimeFormatter DATE_WITHOUT_TIME_FORMAT = DateTimeFormatter.ofPattern( "dd.MM.yyyy" );
	private static final DateTimeFormatter DATE_WITH_TIME_FORMAT = DateTimeFormatter.ofPattern( "dd.MM.yyyy HH:mm" );

	private final WOAssociation _valueAssociation;
	private final WOAssociation _valueWhenEmptyAssociation;
	private final WOAssociation _formatterAssociation;
	private final WOAssociation _patternAssociation; // FIXME: Missing implementation logic for this binding

	public ERXDate( String name, NSDictionary<String, WOAssociation> associations, WOElement template ) {
		super( name, associations, template );
		_valueAssociation = associations.objectForKey( "value" );
		_valueWhenEmptyAssociation = associations.objectForKey( "valueWhenEmpty" );
		_formatterAssociation = associations.objectForKey( "formatter" );
		_patternAssociation = associations.objectForKey( "pattern" );

		if( _valueAssociation == null ) {
			throw new WODynamicElementCreationException( "value binding not specified" );
		}

		if( _formatterAssociation != null && _patternAssociation != null ) {
			throw new WODynamicElementCreationException( "You must only specify either 'formatter' OR 'pattern' bindings, not both" );
		}
	}

	@Override
	public void appendToResponse( WOResponse response, WOContext context ) {
		final Object value = _valueAssociation.valueInComponent( context.component() );

		if( value != null ) {
			response.appendContentString( stringValue( value ) );
		}
		else {
			if( _valueWhenEmptyAssociation != null ) {
				String valueWhenEmpty = (String)_valueWhenEmptyAssociation.valueInComponent( context.component() );
				response.appendContentString( valueWhenEmpty );
			}
		}
	}

	private static String stringValue( final Object value ) {

		if( value instanceof LocalDate ) {
			return DATE_WITHOUT_TIME_FORMAT.format( (TemporalAccessor)value );
		}

		if( value instanceof LocalDateTime ) {
			return DATE_WITH_TIME_FORMAT.format( (TemporalAccessor)value );
		}

		throw new IllegalArgumentException( "I only do dates. You sent me an object type I can't handle: " + value.getClass() );
	}
}