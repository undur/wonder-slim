package er.extensions.routing;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;

import er.routing.conversion.Converters;



/**
 * A page route's handler: a new instance of the page, with the route's parameters
 * (its path's and its host's) set on it by name, converted to the type of the page's member of that name. The member is
 * a public field, a public setter ({@code setBook( Book )}), or a public method taking the value ({@code book( Book )},
 * as a page's fluent setter is). A page lacking one is refused when the route is declared. Only route parameters are set:
 * query values aren't, so a URL can't write any of the page's fields. A typed route's page ({@link #forRecord}) gets its
 * record's components, query parameters included, since the record declares them.
 */

final class PageSetters implements RouteHandler {

	private static final Logger logger = LoggerFactory.getLogger( PageSetters.class );

	/**
	 * Sets one parameter on a page
	 */
	private record Setter( String name, Class<?> type, Member member ) {}

	@FunctionalInterface
	private interface Member {
		void set( Object page, Object value ) throws ReflectiveOperationException;
	}

	private final Class<? extends WOComponent> _pageClass;
	private final Converters _converters;
	private final List<Setter> _setters;

	/**
	 * @throws IllegalArgumentException if the page has no member for a parameter, or one of a type without a converter
	 */
	PageSetters( final Class<? extends WOComponent> pageClass, final List<String> parameterNames, final Converters converters, final String pattern, final List<String> groupParameters ) {
		_pageClass = Objects.requireNonNull( pageClass );
		_converters = converters;
		_setters = new ArrayList<>();

		for( final String name : parameterNames ) {
			final Setter setter = setter( pageClass, name );

			// A group's parameter is the group's: set on a page that has a member for it, and left out of one that doesn't
			if( setter == null && groupParameters.contains( name ) ) {
				continue;
			}

			if( setter == null ) {
				throw new IllegalArgumentException( "The route %s sets its parameter {%s} on %s, which has no public field '%s', setter set%s( … ) or method %s( … ) for it".formatted( pattern, name, pageClass.getSimpleName(), name, capitalized( name ), name ) );
			}

			if( !converters.converts( setter.type() ) ) {
				throw new IllegalArgumentException( "The route %s sets {%s} on %s as a %s, which has no converter. Register one in the router's converters before declaring the route".formatted( pattern, name, pageClass.getSimpleName(), setter.type().getSimpleName() ) );
			}

			_setters.add( setter );
		}
	}

	/**
	 * @return The page answering the route
	 */
	Class<? extends WOComponent> pageClass() {
		return _pageClass;
	}

	@Override
	public WOActionResults handle( final RouteInvocation invocation ) {
		final Object[] values = new Object[_setters.size()];
		final boolean[] absent = new boolean[_setters.size()];

		// Every parameter first, so a request that declines or redirects makes no page
		for( int i = 0; i < _setters.size(); i++ ) {
			final Setter setter = _setters.get( i );
			final String text = invocation.parameter( setter.name() );

			// An optional parameter the request leaves out ({cartoon?}): the page keeps its own value
			if( text == null ) {
				absent[i] = true;
				continue;
			}

			Object value;

			// Converted once per request (a group's parameter is converted before its filters), redirected to its own text
			// if it's other text for the value
			try {
				value = invocation.parameter( setter.name(), setter.type() );
			}
			catch( Declined e ) {
				value = null;
			}

			// As for a typed route: a parameter that isn't one of the type, or names nothing, means the URL is wrong
			if( value == null ) {
				final String reason = "its parameter '%s' is '%s', which isn't a %s, or names none".formatted( setter.name(), text, setter.type().getSimpleName() );
				logger.debug( "The page route to {} declined {}: {}", _pageClass.getSimpleName(), invocation.url(), reason );
				invocation.declinedBecause( reason );
				return RouteHandler.DECLINED;
			}

			values[i] = value;
		}

		final WOComponent page = WOApplication.application().pageWithName( _pageClass.getName(), invocation.context() );

		for( int i = 0; i < _setters.size(); i++ ) {
			final Setter setter = _setters.get( i );

			if( absent[i] ) {
				continue;
			}

			try {
				setter.member().set( page, values[i] );
			}
			catch( ReflectiveOperationException e ) {
				throw new IllegalStateException( "Couldn't set {%s} on %s".formatted( setter.name(), _pageClass.getSimpleName() ), e );
			}
		}

		return page;
	}

	/**
	 * @return A typed route's action answering with a new instance of the page, the record's components set on it by
	 *         name: those that are null (a query parameter absent) aren't set, so the page keeps its own value
	 * @throws IllegalArgumentException if the page has no member for a component (a group's parameter may be left out), or
	 *         one of another type
	 */
	static <P extends Record> Route.Action<P> forRecord( final Class<? extends WOComponent> pageClass, final Class<P> recordClass, final String pattern, final List<String> groupParameters ) {
		Objects.requireNonNull( pageClass );
		final List<RecordComponent> components = new ArrayList<>();
		final List<Setter> setters = new ArrayList<>();

		for( final RecordComponent component : recordClass.getRecordComponents() ) {
			final String name = component.getName();
			final Setter setter = setter( pageClass, name );

			if( setter == null && groupParameters.contains( name ) ) {
				continue;
			}

			if( setter == null ) {
				throw new IllegalArgumentException( "The route %s sets %s.%s on %s, which has no public field '%s', setter set%s( … ) or method %s( … ) for it".formatted( pattern, recordClass.getSimpleName(), name, pageClass.getSimpleName(), name, capitalized( name ), name ) );
			}

			if( !setter.type().isAssignableFrom( Converters.boxed( component.getType() ) ) ) {
				throw new IllegalArgumentException( "The route %s sets %s.%s, a %s, on %s, whose member for it takes a %s".formatted( pattern, recordClass.getSimpleName(), name, component.getType().getSimpleName(), pageClass.getSimpleName(), setter.type().getSimpleName() ) );
			}

			components.add( component );
			setters.add( setter );
		}

		return new RecordPage<>( pageClass, components, setters );
	}

	/**
	 * A typed route's action answering with a new instance of the page, the record's components set on it
	 */
	record RecordPage<P extends Record>( Class<? extends WOComponent> pageClass, List<RecordComponent> components, List<Setter> setters ) implements Route.Action<P> {

		@Override
		public WOActionResults invoke( final P parameters, final RouteInvocation invocation ) {
			final WOComponent page = WOApplication.application().pageWithName( pageClass.getName(), invocation.context() );

			for( int i = 0; i < setters.size(); i++ ) {
				try {
					final Object value = components.get( i ).getAccessor().invoke( parameters );

					if( value != null ) {
						setters.get( i ).member().set( page, value );
					}
				}
				catch( ReflectiveOperationException e ) {
					throw new IllegalStateException( "Couldn't set %s on %s".formatted( components.get( i ).getName(), pageClass.getSimpleName() ), e );
				}
			}

			return page;
		}
	}

	/**
	 * @return How to set the parameter on the page: a method taking it (named as it is, or its setter), or a field
	 */
	private static Setter setter( final Class<?> pageClass, final String name ) {
		final List<Method> methods = new ArrayList<>();

		for( final Method method : pageClass.getMethods() ) {
			if( method.getParameterCount() == 1 && !Modifier.isStatic( method.getModifiers() ) && !method.isBridge() && (method.getName().equals( name ) || method.getName().equals( "set" + capitalized( name ) )) ) {
				methods.add( method );
			}
		}

		// Several (overloads, a setter and a fluent one) take the value as different types: which is meant isn't clear
		if( methods.stream().map( m -> m.getParameterTypes()[0] ).distinct().count() > 1 ) {
			throw new IllegalArgumentException( "%s has several methods taking {%s}, of different types: %s. A page route sets a parameter through one".formatted( pageClass.getSimpleName(), name, methods.stream().map( m -> m.getName() + "( " + m.getParameterTypes()[0].getSimpleName() + " )" ).toList() ) );
		}

		if( !methods.isEmpty() ) {
			final Method method = methods.getFirst();
			return new Setter( name, Converters.boxed( method.getParameterTypes()[0] ), ( page, value ) -> method.invoke( page, value ) );
		}

		try {
			final Field field = pageClass.getField( name );

			if( !Modifier.isStatic( field.getModifiers() ) && !Modifier.isFinal( field.getModifiers() ) ) {
				return new Setter( name, Converters.boxed( field.getType() ), field::set );
			}
		}
		catch( NoSuchFieldException e ) {
			// Neither
		}

		return null;
	}

	private static String capitalized( final String name ) {
		return name.isEmpty() ? name : Character.toUpperCase( name.charAt( 0 ) ) + name.substring( 1 );
	}
}
