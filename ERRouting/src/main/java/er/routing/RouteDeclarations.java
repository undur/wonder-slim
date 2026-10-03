package er.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EXPERIMENTAL (route-links branch). The routes an application and its plugins declared, and the router they're in:
 * declared again, in the order they were first declared, into a new router when their classes change (in development),
 * which replaces the current one once they all succeed. Requests during a declaration keep the current routes.
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
	 * Holds the router being declared into, for {@link ERXRouter#defaultRouter()} called by a declaration
	 */
	static final ThreadLocal<ERXRouter> DECLARING = new ThreadLocal<>();

	private final Supplier<ERXRouter> _routerFactory;
	private final Changes _changes;
	private final List<Declared<?>> _declared = new CopyOnWriteArrayList<>();
	private final ReentrantLock _lock = new ReentrantLock();
	private volatile ERXRouter _router;

	/**
	 * Why the routes couldn't be declared again, null if they could
	 */
	private volatile RuntimeException _failure;

	/**
	 * True once it's logged that the routes aren't declared again, since one was mapped outside a declaration
	 */
	private volatile boolean _reloadRefusalLogged;

	RouteDeclarations( final Supplier<ERXRouter> routerFactory, final Changes changes ) {
		_routerFactory = Objects.requireNonNull( routerFactory );
		_changes = Objects.requireNonNull( changes );
		_router = routerFactory.get();
	}

	/**
	 * @return The router of the current routes
	 */
	ERXRouter router() {
		return _router;
	}

	/**
	 * Declares routes in the current router, and again whenever the routes are declared again
	 */
	<T> Declared<T> declare( final Function<ERXRouter, T> declaration ) {
		final Declared<T> declared = new Declared<>( declaration );
		_lock.lock();

		try {
			final T holder = declaring( _router, () -> declared.declare( _router ) );
			declared.set( holder );
			_declared.add( declared );
			watch( holder, _router );
			_changes.built();
		}
		finally {
			_lock.unlock();
		}

		return declared;
	}

	/**
	 * Declares the routes again if their classes changed, unless that's happening already (then the current routes
	 * answer)
	 *
	 * @throws IllegalStateException if declaring them again failed, until they're declared again: the routes would be
	 *         the old ones, which a developer would test without knowing
	 */
	void declareAgainIfChanged() {

		// A route or a converter added outside a declaration would be lost
		final String undeclared = _router.undeclared();

		if( undeclared != null ) {
			if( !_reloadRefusalLogged && _changes.changed() ) {
				_reloadRefusalLogged = true;
				logger.warn( "The routes aren't declared again when their classes change, since {} was added outside a declaration (ERXRouter.declare()), and it would be lost", undeclared );
			}

			return;
		}

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
	 * Declares every route again in a new router, which becomes the current one if they all succeed
	 */
	void declareAgain() {
		_lock.lock();

		try {
			final ERXRouter router = _routerFactory.get();
			final List<Object> holders = new ArrayList<>();

			try {
				declaring( router, () -> {
					for( final Declared<?> declared : _declared ) {
						holders.add( declared.declare( router ) );
					}

					router.checkJoins();
					return null;
				} );
			}
			catch( RuntimeException e ) {
				_failure = e;
				_changes.built();
				logger.error( "The routes couldn't be declared again, so the previous ones are in place", e );
				return;
			}

			for( int i = 0; i < holders.size(); i++ ) {
				_declared.get( i ).set( holders.get( i ) );
				watch( holders.get( i ), router );
			}

			_router = router;
			_failure = null;
			_changes.built();
			logger.info( "Declared the routes again: {} routes", router.routes().size() );
		}
		finally {
			_lock.unlock();
		}
	}

	private void watch( final Object holder, final ERXRouter router ) {
		_changes.watch( holder.getClass() );
		router.routes().stream().map( RouteDescription::parametersClass ).filter( Objects::nonNull ).distinct().forEach( _changes::watch );
	}

	private static <T> T declaring( final ERXRouter router, final Supplier<T> declaration ) {
		final ERXRouter previous = DECLARING.get();
		DECLARING.set( router );

		try {
			return declaration.get();
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
