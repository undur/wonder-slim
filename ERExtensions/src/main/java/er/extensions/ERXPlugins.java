package er.extensions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The application's plugins (see {@link ERXPlugin}): found through {@link ServiceLoader} and ordered by what they
 * require, never by classpath order. Loaded in {@code ERXApplication.main()}.
 */
public final class ERXPlugins {

	/**
	 * The plugins, in order. Empty until loaded.
	 */
	private static volatile List<ERXPlugin> _plugins = List.of();

	private ERXPlugins() {}

	/**
	 * Finds, constructs and orders the plugins. Invoked once, from {@code ERXApplication.main()}.
	 */
	public static synchronized List<ERXPlugin> load() {

		if( !_plugins.isEmpty() ) {
			throw new IllegalStateException( "The plugins have already been loaded" );
		}

		final List<ERXPlugin> found = ServiceLoader
				.load( ERXPlugin.class )
				.stream()
				.map( ServiceLoader.Provider::get )
				.toList();

		_plugins = ordered( found );
		return _plugins;
	}

	/**
	 * @return The plugins, in order
	 */
	public static List<ERXPlugin> all() {
		return _plugins;
	}

	/**
	 * Runs the given action on every plugin, in order
	 */
	public static void forEach( final Consumer<ERXPlugin> action ) {
		_plugins.forEach( action );
	}

	/**
	 * @return The given plugins ordered so that each comes after the plugins it requires. Plugins that don't depend on
	 *         each other are ordered by class name, so the order is the same whatever the classpath order.
	 * @throws IllegalStateException If a plugin requires one that isn't present, or plugins require each other in a cycle
	 */
	static List<ERXPlugin> ordered( final List<ERXPlugin> plugins ) {
		final Map<Class<?>, ERXPlugin> byClass = new HashMap<>();

		for( final ERXPlugin plugin : plugins ) {
			if( byClass.put( plugin.getClass(), plugin ) != null ) {
				throw new IllegalStateException( "The plugin " + plugin.getClass().getName() + " is listed more than once" );
			}
		}

		// For each plugin, the plugins it still waits for
		final Map<ERXPlugin, List<ERXPlugin>> waitingFor = new HashMap<>();

		for( final ERXPlugin plugin : plugins ) {
			final List<ERXPlugin> required = new ArrayList<>();

			for( final Class<? extends ERXPlugin> requiredClass : plugin.requires() ) {
				final ERXPlugin requiredPlugin = byClass.get( requiredClass );

				if( requiredPlugin == null ) {
					throw new IllegalStateException( "The plugin " + plugin.getClass().getName() + " requires " + requiredClass.getName() + ", which isn't present. Is its framework missing from the classpath, or does its jar lack META-INF/services/" + ERXPlugin.class.getName() + "?" );
				}

				required.add( requiredPlugin );
			}

			waitingFor.put( plugin, required );
		}

		// Kahn's algorithm, taking the ready plugin with the lowest class name first
		final TreeMap<String, ERXPlugin> ready = new TreeMap<>();
		final List<ERXPlugin> result = new ArrayList<>();

		for( final ERXPlugin plugin : plugins ) {
			if( waitingFor.get( plugin ).isEmpty() ) {
				ready.put( plugin.getClass().getName(), plugin );
			}
		}

		while( !ready.isEmpty() ) {
			final ERXPlugin next = ready.pollFirstEntry().getValue();
			result.add( next );

			for( final Map.Entry<ERXPlugin, List<ERXPlugin>> entry : waitingFor.entrySet() ) {
				if( entry.getValue().remove( next ) && entry.getValue().isEmpty() ) {
					ready.put( entry.getKey().getClass().getName(), entry.getKey() );
				}
			}
		}

		if( result.size() < plugins.size() ) {
			final List<ERXPlugin> cycle = plugins
					.stream()
					.filter( plugin -> !result.contains( plugin ) )
					.sorted( Comparator.comparing( plugin -> plugin.getClass().getName() ) )
					.toList();

			throw new IllegalStateException( "These plugins require each other in a cycle: " + names( cycle ) );
		}

		return List.copyOf( result );
	}

	private static String names( final Iterable<ERXPlugin> plugins ) {
		final List<String> names = new ArrayList<>();
		plugins.forEach( plugin -> names.add( plugin.getClass().getName() ) );
		return names.stream().collect( Collectors.joining( ", " ) );
	}
}
