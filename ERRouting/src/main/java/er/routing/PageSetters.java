package er.routing;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOComponent;

import er.routing.core.Converters;

/**
 * EXPERIMENTAL (route-links branch). A page route's handler: a new instance of the page, with the route's parameters
 * (its path's and its host's) set on it by name, converted to the type of the page's member of that name. The member is
 * a public field, a public setter ({@code setBook( Book )}), or a public method taking the value ({@code book( Book )},
 * as a page's fluent setter is). A page lacking one is refused when the route is declared. Only route parameters are set:
 * query values aren't, so a URL can't write any of the page's fields.
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
	PageSetters( final Class<? extends WOComponent> pageClass, final List<String> parameterNames, final Converters converters, final String pattern ) {
		_pageClass = Objects.requireNonNull( pageClass );
		_converters = converters;
		_setters = new ArrayList<>();

		for( final String name : parameterNames ) {
			final Setter setter = setter( pageClass, name );

			if( setter == null ) {
				throw new IllegalArgumentException( "The route %s sets its parameter {%s} on %s, which has no public field '%s', setter set%s( … ) or method %s( … ) for it".formatted( pattern, name, pageClass.getSimpleName(), name, capitalized( name ), name ) );
			}

			if( !converters.converts( setter.type() ) ) {
				throw new IllegalArgumentException( "The route %s sets {%s} on %s as a %s, which has no converter. Register one in the router's converters before declaring the route".formatted( pattern, name, pageClass.getSimpleName(), setter.type().getSimpleName() ) );
			}

			_setters.add( setter );
		}
	}

	@Override
	public WOActionResults handle( final RouteInvocation invocation ) {
		final Object[] values = new Object[_setters.size()];

		// Every parameter first, so a request that declines or redirects makes no page
		for( int i = 0; i < _setters.size(); i++ ) {
			final Setter setter = _setters.get( i );
			final String text = invocation.parameter( setter.name() );
			Object value;

			try {
				value = _converters.fromString( text, setter.type(), invocation );
			}
			catch( IllegalArgumentException e ) {
				value = null;
			}

			// As for a typed route: a parameter that isn't one of the type, or names nothing, means the URL is wrong
			if( value == null ) {
				final String reason = "its parameter '%s' is '%s', which isn't a %s, or names none".formatted( setter.name(), text, setter.type().getSimpleName() );
				logger.debug( "The page route to {} declined {}: {}", _pageClass.getSimpleName(), invocation.url(), reason );
				invocation.declinedBecause( reason );
				return RouteHandler.DECLINED;
			}

			if( !_converters.isCanonical( text, value ) ) {
				throw new NotCanonical( setter.name(), _converters.toString( value ) );
			}

			values[i] = value;
		}

		final WOComponent page = WOApplication.application().pageWithName( _pageClass.getName(), invocation.context() );

		for( int i = 0; i < _setters.size(); i++ ) {
			final Setter setter = _setters.get( i );

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
