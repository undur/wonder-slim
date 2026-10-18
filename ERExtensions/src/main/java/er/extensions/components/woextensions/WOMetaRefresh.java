package er.extensions.components.woextensions;

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

/**
 * Renders a {@code <meta http-equiv="refresh">} tag that, after {@code seconds}, requests a component action URL. That
 * action returns the page named by {@code pageName}, or the result of {@code action}.
 *
 * Carried over from JavaWOExtensions, where it was a component.
 */

public class WOMetaRefresh extends WODynamicElement {

	private final WOAssociation _seconds;
	private final WOAssociation _pageName;
	private final WOAssociation _action;

	public WOMetaRefresh( final String name, final NSDictionary<String, WOAssociation> associations, final WOElement template ) {
		super( name, associations, template );

		_seconds = associations.objectForKey( "seconds" );
		_pageName = associations.objectForKey( "pageName" );
		_action = associations.objectForKey( "action" );

		if( _seconds == null ) {
			throw new WODynamicElementCreationException( "<%s> 'seconds' is a required binding".formatted( getClass().getName() ) );
		}

		if( _pageName == null && _action == null ) {
			throw new WODynamicElementCreationException( "<%s> must supply either 'pageName' or 'action'".formatted( getClass().getName() ) );
		}
	}

	@Override
	public void appendToResponse( final WOResponse response, final WOContext context ) {
		final Object seconds = _seconds.valueInComponent( context.component() );

		if( seconds == null ) {
			throw new IllegalStateException( "<%s> 'seconds' is null".formatted( getClass().getName() ) );
		}

		response.appendContentString( "<meta http-equiv=\"refresh\"" );
		response._appendTagAttributeAndValue( "content", seconds + ";url=" + context.componentActionURL(), true );
		response.appendContentString( " />" );
	}

	@Override
	public WOActionResults invokeAction( final WORequest request, final WOContext context ) {
		if( !context.elementID().equals( context.senderID() ) ) {
			return null;
		}

		final WOComponent component = context.component();
		final WOActionResults result;

		if( _pageName != null ) {
			final Object pageName = _pageName.valueInComponent( component );

			if( pageName == null ) {
				throw new IllegalStateException( "<%s> 'pageName' is null".formatted( getClass().getName() ) );
			}

			result = component.pageWithName( pageName.toString() );
		}
		else {
			result = (WOActionResults)_action.valueInComponent( component );
		}

		return result != null ? result : context.page();
	}
}
