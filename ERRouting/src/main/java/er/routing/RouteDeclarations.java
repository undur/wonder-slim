package er.routing;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EXPERIMENTAL (route-links branch). The route declarations of an application and its plugins, and the router they
 * declared into: run again, in the order they were first made, into a new router when their classes change (in
 * development), which replaces the current one once they all succeed. Requests during a declaration keep the current
 * routes.
 */

class RouteDeclarations {

	private static final Logger logger = LoggerFactory.getLogger( RouteDeclarations.class );

	/**
	 * Whether the classes declarations depend on changed
	 */
	interface Changes {

		/**
		 * Watches a class declarations depend on
		 */
		void watch( Class<?> type );

		/**
		 * @return true if a watched class changed since the declarations last ran
		 */
		boolean changed();

		/**
		 * The declarations ran
		 */
		void built();

		/**
		 * Never changes: routes declared once (deployed)
		 */
		Changes NONE = new Changes() {
			@Override
			public void watch( final Class<?> type ) {}

			@Override
			public boolean changed() {
				return false;
			}

			@Override
			public void built() {}
		};
	}

	/**
	 * Holds the router being declared into, for {@link ERXRouter#defaultRouter()} called by a declaration, and for the
	 * routes it gives patterns to
	 */
	static final ThreadLocal<ERXRouter> DECLARING = new ThreadLocal<>();

	private final Supplier<ERXRouter> _routerFactory;
	private final Changes _changes;
	private final Function<ERXRouter, List<RouteIdentity<?>>> _undeclaredConstants;
	private final List<Consumer<ERXRouter>> _declarations = new CopyOnWriteArrayList<>();
	private final ReentrantLock _lock = new ReentrantLock();
	private volatile ERXRouter _router;

	/**
	 * Why the routes couldn't be declared again, null if they could
	 */
	private volatile RuntimeException _failure;

	RouteDeclarations( final Supplier<ERXRouter> routerFactory, final Changes changes ) {
		this( routerFactory, changes, RouteIdentity::undeclaredIn );
	}

	/**
	 * @param undeclaredConstants The route constants a router doesn't declare, which fail a declaration
	 */
	RouteDeclarations( final Supplier<ERXRouter> routerFactory, final Changes changes, final Function<ERXRouter, List<RouteIdentity<?>>> undeclaredConstants ) {
		_routerFactory = Objects.requireNonNull( routerFactory );
		_changes = Objects.requireNonNull( changes );
		_undeclaredConstants = Objects.requireNonNull( undeclaredConstants );
		_router = newRouter();
	}

	private ERXRouter newRouter() {
		final ERXRouter router = _routerFactory.get();
		router.declaredOnly();
		return router;
	}

	/**
	 * @return The router of the current routes
	 */
	ERXRouter router() {
		return _router;
	}

	/**
	 * Runs a declaration in the current router, and again whenever the routes are declared again
	 *
	 * @param declaredIn The class making the declaration, whose folder is watched
	 */
	void declare( final Consumer<ERXRouter> declaration, final Class<?> declaredIn ) {
		Objects.requireNonNull( declaration );
		_lock.lock();

		try {
			declaring( _router, () -> declaration.accept( _router ) );
			_router.publishBindings();
			_declarations.add( declaration );

			if( declaredIn != null ) {
				_changes.watch( declaredIn );
			}

			watch( _router );
			_changes.built();
		}
		finally {
			_lock.unlock();
		}
	}

	/**
	 * Checks that every route constant made is declared, once all the declarations have run (the application launching)
	 *
	 * @throws IllegalStateException naming those that aren't
	 */
	void checkDeclared() {
		checkDeclared( _router );
	}

	private void checkDeclared( final ERXRouter router ) {
		final List<RouteIdentity<?>> undeclared = _undeclaredConstants.apply( router );

		if( !undeclared.isEmpty() ) {
			throw new IllegalStateException( "Routes are made that no route declaration gives a pattern to: %s. Map them in ERXRouter.declare( router -> … ), with route( pattern, route ) or map( pattern, route, … )".formatted( undeclared.stream().map( RouteIdentity::name ).toList() ) );
		}
	}

	/**
	 * Declares the routes again if their classes changed, unless that's happening already (then the current routes
	 * answer)
	 *
	 * @throws IllegalStateException if declaring them again failed, until they're declared again: the routes would be
	 *         the old ones, which a developer would test without knowing
	 */
	void declareAgainIfChanged() {

		if( _changes.changed() && _lock.tryLock() ) {
			try {
				if( _changes.changed() ) {
					declareAgain();
				}
			}
			finally {
				_lock.unlock();
			}
		}

		final RuntimeException failure = _failure;

		if( failure != null ) {
			throw new IllegalStateException( "The routes couldn't be declared again, so the previous ones are in place: " + failure.getMessage(), failure );
		}
	}

	/**
	 * Runs every declaration again in a new router, which becomes the current one if they all succeed
	 */
	void declareAgain() {
		_lock.lock();

		try {
			final ERXRouter router = newRouter();

			try {
				declaring( router, () -> {
					_declarations.forEach( declaration -> declaration.accept( router ) );
					router.checkJoins();
					checkDeclared( router );
				} );
			}
			catch( RuntimeException e ) {
				_failure = e;
				_changes.built();
				logger.error( "The routes couldn't be declared again, so the previous ones are in place", e );
				return;
			}

			router.publishBindings();
			watch( router );
			_router = router;
			_failure = null;
			_changes.built();
			logger.info( "Declared the routes again: {} routes", router.routes().size() );
		}
		finally {
			_lock.unlock();
		}
	}

	/**
	 * Watches the classes of the router's route constants (their constants interfaces) and of its routes' records
	 */
	private void watch( final ERXRouter router ) {
		router.declaringClasses().forEach( _changes::watch );
		router.bindings().keySet().stream().map( RouteIdentity::madeIn ).filter( Objects::nonNull ).distinct().forEach( _changes::watch );
		router.routes().stream().map( RouteDescription::parametersClass ).filter( Objects::nonNull ).distinct().forEach( _changes::watch );
	}

	private static void declaring( final ERXRouter router, final Runnable declaration ) {
		final ERXRouter previous = DECLARING.get();
		DECLARING.set( router );

		try {
			declaration.run();
		}
		finally {
			if( previous == null ) {
				DECLARING.remove();
			}
			else {
				DECLARING.set( previous );
			}
		}
	}
}
