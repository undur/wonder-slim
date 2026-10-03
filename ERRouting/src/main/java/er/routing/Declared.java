package er.routing;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * EXPERIMENTAL (route-links branch). Routes an application or a plugin declared, and what declaring them returned: the
 * object holding them, for links ({@code BookclubRoutes}). In development the declaration runs again when its classes
 * change, so {@link #get()} is the holder of the current routes: read it each time, don't keep it.
 *
 * <pre>
 * private static Declared&lt;BookclubRoutes&gt; _routes;
 *
 * public static void declare() {
 *     _routes = ERXRouter.declare( BookclubRoutes::new );
 * }
 *
 * public static BookclubRoutes instance() {
 *     return _routes.get();
 * }
 * </pre>
 *
 * A holder is made again with each declaration, so state that should outlive it (a plugin's data) is kept elsewhere.
 */

public final class Declared<T> implements Supplier<T> {

	private final Function<ERXRouter, T> _declaration;
	private volatile T _holder;

	Declared( final Function<ERXRouter, T> declaration ) {
		_declaration = Objects.requireNonNull( declaration );
	}

	/**
	 * @return The holder of the current routes
	 */
	@Override
	public T get() {
		return _holder;
	}

	/**
	 * @return A new holder, its routes declared in the router (not yet the current one)
	 */
	T declare( final ERXRouter router ) {
		return Objects.requireNonNull( _declaration.apply( router ), "A route declaration returned null: it returns the object holding its routes" );
	}

	@SuppressWarnings("unchecked")
	void set( final Object holder ) {
		_holder = (T)holder;
	}
}
