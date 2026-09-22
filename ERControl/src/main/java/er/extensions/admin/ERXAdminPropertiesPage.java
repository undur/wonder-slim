package er.extensions.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSProperties;

import er.extensions.ERXLoggingSupport;
import er.extensions.components.ERXComponent;
import er.extensions.foundation.ERXProperties;

/**
 * Every property in effect, with the ones a Properties file or the command line set marked, and secrets masked.
 * A property can be set on the running instance; it lasts until the instance stops.
 */
public class ERXAdminPropertiesPage extends ERXComponent {

	public record Property( String key, String value, boolean explicit, boolean secret ) {}

	public Property current;
	public String filter;
	public boolean onlyExplicit;

	public String newKey;
	public String newValue;
	public String notice;

	public ERXAdminPropertiesPage( WOContext context ) {
		super( context );
	}

	public List<Property> properties() {
		final Set<String> explicit = ERXProperties.explicitlySetKeys();
		final String needle = filter == null || filter.isBlank() ? null : filter.toLowerCase();
		final List<Property> result = new ArrayList<>();

		for( final String key : new TreeSet<>( NSProperties._getProperties().stringPropertyNames() ) ) {
			final boolean isExplicit = explicit.contains( key );

			if( onlyExplicit && !isExplicit ) {
				continue;
			}

			final boolean secret = ERXProperties.isSecretKey( key );
			final String raw = NSProperties.getProperty( key );
			final String value = secret ? "********" : raw == null ? "" : raw;

			if( needle != null && !key.toLowerCase().contains( needle ) && !( !secret && value.toLowerCase().contains( needle ) ) ) {
				continue;
			}

			result.add( new Property( key, value, isExplicit, secret ) );
		}

		return result;
	}

	public int propertyCount() {
		return properties().size();
	}

	public WOActionResults apply() {
		return null;
	}

	public WOActionResults setProperty() {

		if( newKey == null || newKey.isBlank() ) {
			notice = "Give the property a key.";
			return null;
		}

		final String key = newKey.trim();
		System.setProperty( key, newValue == null ? "" : newValue );
		ERXLoggingSupport.configureLoggingWithSystemProperties();
		notice = "Set " + key + " on this instance. It lasts until the instance stops.";
		filter = key;
		newKey = null;
		newValue = null;
		return null;
	}
}
