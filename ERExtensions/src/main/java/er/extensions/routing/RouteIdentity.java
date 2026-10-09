package er.extensions.routing;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What a route constant is: an identity a declaration binds to a pattern in a router
 * ({@link Route}, {@link PlainRoute}). Links go through it to the binding of the current routes, so a constant stays the
 * same object while the routes are declared again (in development) and its pattern changes.
 *
 * @param <B> The binding: the route as one router has it
 */

abstract sealed class RouteIdentity<B> permits Route, PlainRoute {

	/**
	 * The route constants made ({@code Route.of( … )}, {@code Route.plain()}): each must be declared, or the application
	 * doesn't start
	 */
	private static final Set<RouteIdentity<?>> CONSTANTS = Collections.synchronizedSet( Collections.newSetFromMap( new WeakHashMap<>() ) );

	/**
	 * The class the identity was made in (a constants interface), null for one a declaration made itself
	 */
	private final Class<?> _madeIn;
	private volatile B _binding;
	private volatile String _name;

	/**
	 * True for a constant a declaration may leave without a pattern (a route mapped in development only)
	 */
	private volatile boolean _optional;

	RouteIdentity( final Class<?> madeIn ) {
		_madeIn = madeIn;

		if( madeIn != null ) {
			CONSTANTS.add( this );
		}
	}

	/**
	 * @return The class that made the route constant, null for a route a declaration made itself
	 */
	Class<?> madeIn() {
		return _madeIn;
	}

	/**
	 * @return The route as the router being declared into has it, if a declaration is running and has bound it, otherwise
	 *         as the current routes have it
	 * @throws IllegalStateException if it isn't declared
	 */
	B binding() {
		final ERXRouter declaring = RouteDeclarations.DECLARING.get();

		if( declaring != null ) {
			@SuppressWarnings("unchecked")
			final B pending = (B)declaring.binding( this );

			if( pending != null ) {
				return pending;
			}
		}

		final B binding = _binding;

		if( binding == null ) {
			throw new IllegalStateException( "The route %s isn't declared: map it in a route declaration (ERXRouter.declare( routes -> … )), with map( pattern, %s, … )".formatted( name(), name() ) );
		}

		return binding;
	}

	void markOptional() {
		_optional = true;
	}

	boolean isBound() {
		return _binding != null;
	}

	/**
	 * Makes the binding the current one, once the routes it was declared with are in place
	 */
	@SuppressWarnings("unchecked")
	void publish( final Object binding ) {
		_binding = (B)binding;
	}

	/**
	 * @return The route constants made that aren't declared in the router. A constant its class no longer holds was
	 *         replaced (a hot swap running the class's initializer again, adding a constant), so it's let go of.
	 */
	static List<RouteIdentity<?>> undeclaredIn( final ERXRouter router ) {
		final List<RouteIdentity<?>> undeclared = new ArrayList<>();

		synchronized( CONSTANTS ) {
			CONSTANTS.removeIf( identity -> identity.heldField() == null );

			for( final RouteIdentity<?> identity : CONSTANTS ) {
				if( !identity._optional && router.binding( identity ) == null ) {
					undeclared.add( identity );
				}
			}
		}

		return undeclared;
	}

	/**
	 * @return The constant's name ({@code Routes.search}), found among its class's static fields, or a description
	 */
	String name() {
		String name = _name;

		if( name == null ) {
			name = constantName();
			_name = name;
		}

		return name;
	}

	/**
	 * @return The constant's name ({@code Routes.search}), null for a route no constant holds
	 */
	String constantNameOrNull() {
		final Field field = heldField();
		return field == null ? null : _madeIn.getSimpleName() + "." + field.getName();
	}

	private String constantName() {
		final Field field = heldField();
		return field == null ? description() : _madeIn.getSimpleName() + "." + field.getName();
	}

	/**
	 * @return The static field of the class that made it holding the constant, null if none does
	 */
	private Field heldField() {

		if( _madeIn != null ) {
			for( final Field field : _madeIn.getDeclaredFields() ) {
				if( Modifier.isStatic( field.getModifiers() ) ) {
					try {
						field.setAccessible( true );

						if( field.get( null ) == this ) {
							return field;
						}
					}
					catch( ReflectiveOperationException | RuntimeException e ) {
						// Not readable, so not this one
					}
				}
			}
		}

		return null;
	}

	/**
	 * @return What the route is, for messages, when it isn't a named constant
	 */
	abstract String description();

	/**
	 * @return The names a link to the route gives values for
	 */
	abstract List<String> acceptedNames();

	/**
	 * @return The names a link to the route must give values for (the host's parameters can be the request's)
	 */
	abstract List<String> requiredNames();

	/**
	 * @return The class mapping a route: the first on the stack outside the router's own packages
	 */
	static Class<?> mapper() {
		return StackWalker.getInstance( StackWalker.Option.RETAIN_CLASS_REFERENCE ).walk( frames -> frames.map( StackWalker.StackFrame::getDeclaringClass ).filter( type -> !(type.getPackageName().equals( "er.routing" ) || type.getPackageName().startsWith( "er.routing." )) ).findFirst().orElse( null ) );
	}

	/**
	 * @return The first class on the stack outside an API: who called into it, however many of its own methods (an
	 *         overload delegating to another) the call went through
	 */
	static Class<?> callerOf( final Class<?> api ) {
		return StackWalker.getInstance( StackWalker.Option.RETAIN_CLASS_REFERENCE ).walk( frames -> frames.map( StackWalker.StackFrame::getDeclaringClass ).filter( type -> type != RouteIdentity.class && type != api ).findFirst().orElse( null ) );
	}

	/**
	 * @return The class calling the method that made an identity: two frames up from the factory
	 */
	static Class<?> caller() {
		return StackWalker.getInstance( StackWalker.Option.RETAIN_CLASS_REFERENCE ).walk( frames -> frames.skip( 2 ).findFirst().map( StackWalker.StackFrame::getDeclaringClass ).orElse( null ) );
	}
}
