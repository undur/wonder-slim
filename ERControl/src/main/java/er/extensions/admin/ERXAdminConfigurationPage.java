package er.extensions.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSProperties;

import er.extensions.ERXPlugin;
import er.extensions.ERXPlugins;
import er.extensions.components.ERXComponent;
import er.extensions.foundation.ERXConfigurationManager;
import er.extensions.foundation.ERXConfigurationManager.Source;

/**
 * The application's configuration: the plugins in the order they run, the sources of properties in the order they're
 * applied, and every property in effect with the source it came from and the sources it overrides. Secrets are
 * masked. A property can be set on the running instance; it lasts until the instance stops.
 */
public class ERXAdminConfigurationPage extends ERXComponent {

	/**
	 * A plugin, as listed: its class, the framework it's in and what it requires
	 */
	public record PluginRow( String name, String className, String framework, String requires ) {}

	/**
	 * A source, as listed: its position, name and location, how many properties it sets and how many of those are in effect
	 */
	public record SourceRow( int position, Source source, int count, int inEffect ) {

		public int overridden() {
			return count - inEffect;
		}
	}

	/**
	 * A property, as listed. {@code origin} is the source its value came from (empty for a WebObjects or JVM default),
	 * {@code overrides} the sources before it that set it too. Showing one source: {@code overriddenBy} is the source
	 * whose value is in effect instead of this source's, empty if this one's is.
	 */
	public record PropertyRow( String key, String value, String origin, String overrides, String overriddenBy, boolean secret, boolean changedHere, boolean setOnThisInstance ) {

		public boolean hasOrigin() {
			return !origin.isEmpty();
		}

		public boolean hasOverrides() {
			return !overrides.isEmpty();
		}

		public boolean isOverridden() {
			return !overriddenBy.isEmpty();
		}
	}

	public PluginRow currentPlugin;
	public SourceRow currentSource;
	public PropertyRow current;

	public String filter;
	public boolean onlyExplicit;

	/**
	 * The source whose properties are shown, null for every property in effect
	 */
	public Source selectedSource;

	/**
	 * The source being listed in the choice of sources
	 */
	public Source sourceItem;

	public String newKey;
	public String newValue;
	public String notice;

	public ERXAdminConfigurationPage( WOContext context ) {
		super( context );
	}

	private static ERXConfigurationManager configuration() {
		return ERXConfigurationManager.current();
	}

	public boolean hasConfiguration() {
		return configuration() != null;
	}

	public List<PluginRow> plugins() {
		final List<PluginRow> rows = new ArrayList<>();

		for( final ERXPlugin plugin : ERXPlugins.all() ) {
			final NSBundle bundle = NSBundle.bundleForClass( plugin.getClass() );
			final String requires = plugin.requires().stream().map( Class::getSimpleName ).collect( Collectors.joining( ", " ) );
			rows.add( new PluginRow( plugin.getClass().getSimpleName(), plugin.getClass().getName(), bundle == null ? "" : bundle.name(), requires ) );
		}

		return rows;
	}

	public int pluginCount() {
		return ERXPlugins.all().size();
	}

	public List<SourceRow> sourceRows() {
		final List<SourceRow> rows = new ArrayList<>();

		if( hasConfiguration() ) {
			int position = 1;

			for( final Source source : configuration().sources() ) {
				final int inEffect = (int)source.properties().keySet().stream().filter( key -> configuration().origin( key ) == source ).count();
				rows.add( new SourceRow( position++, source, source.properties().size(), inEffect ) );
			}
		}

		return rows;
	}

	/**
	 * @return The sources that were found, for choosing one to show
	 */
	public List<Source> sources() {
		return hasConfiguration() ? configuration().sources().stream().filter( Source::found ).toList() : List.of();
	}

	public String sourceItemLabel() {
		return sourceItem.name() + ( sourceItem.location().isEmpty() ? "" : " (" + sourceItem.location() + ")" );
	}

	public List<PropertyRow> properties() {
		return selectedSource != null ? propertiesOf( selectedSource ) : propertiesInEffect();
	}

	/**
	 * @return Every property in effect
	 */
	private List<PropertyRow> propertiesInEffect() {
		final List<PropertyRow> result = new ArrayList<>();

		for( final String key : new TreeSet<>( NSProperties._getProperties().stringPropertyNames() ) ) {
			final Source origin = hasConfiguration() ? configuration().origin( key ) : null;

			if( onlyExplicit && origin == null ) {
				continue;
			}

			final String value = NSProperties.getProperty( key );
			final boolean changedHere = origin != null && !Objects.equals( value, origin.properties().get( key ) );
			final String overrides = origin == null ? "" : configuration()
					.sourcesSetting( key )
					.stream()
					.filter( source -> source != origin )
					.map( Source::name )
					.collect( Collectors.joining( ", " ) );

			addIfMatching( result, key, value, origin == null ? "" : origin.name(), overrides, "", changedHere, origin != null && origin.isInstanceSource() );
		}

		return result;
	}

	/**
	 * @return The properties the given source sets, with the source's own values
	 */
	private List<PropertyRow> propertiesOf( final Source source ) {
		final List<PropertyRow> result = new ArrayList<>();

		for( final Map.Entry<String, String> entry : new TreeMap<>( source.properties() ).entrySet() ) {
			final Source origin = configuration().origin( entry.getKey() );
			addIfMatching( result, entry.getKey(), entry.getValue(), source.name(), "", origin == source ? "" : origin.name(), false, source.isInstanceSource() );
		}

		return result;
	}

	private void addIfMatching( final List<PropertyRow> result, final String key, final String rawValue, final String origin, final String overrides, final String overriddenBy, final boolean changedHere, final boolean setOnThisInstance ) {
		final String needle = filter == null || filter.isBlank() ? null : filter.toLowerCase();
		final boolean secret = ERXConfigurationManager.isSecretKey( key );
		final String value = rawValue == null ? "" : ERXConfigurationManager.maskedValue( key, rawValue );

		if( needle != null && !key.toLowerCase().contains( needle ) && !( !secret && value.toLowerCase().contains( needle ) ) ) {
			return;
		}

		result.add( new PropertyRow( key, value, origin, overrides, overriddenBy, secret, changedHere, setOnThisInstance ) );
	}

	public int propertyCount() {
		return properties().size();
	}

	public boolean showingOneSource() {
		return selectedSource != null;
	}

	public WOActionResults apply() {
		return null;
	}

	public WOActionResults showSource() {
		selectedSource = currentSource.source();
		return null;
	}

	public WOActionResults setProperty() {

		if( newKey == null || newKey.isBlank() ) {
			notice = "Give the property a key.";
			return null;
		}

		final String key = newKey.trim();
		ERXConfigurationManager.setProperty( key, newValue == null ? "" : newValue );
		notice = "Set " + key + " on this instance. It overrides every other source until it's unset or the instance stops.";
		filter = key;
		selectedSource = null;
		newKey = null;
		newValue = null;
		return null;
	}

	/**
	 * Unsets the property in the current row, set on this instance: it gets the value the other sources give it back
	 */
	public WOActionResults unsetProperty() {
		final String key = current.key();
		ERXConfigurationManager.unsetProperty( key );
		notice = "Unset " + key + " on this instance. Its value is the one the other sources give it again, if any.";
		return null;
	}
}
