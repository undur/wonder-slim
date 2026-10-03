package er.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * Routes declared again when their classes change: a new router and new holders, or the current ones kept if a
 * declaration fails
 */
public class RouteDeclarationsTest {

	private static final RouteHandler NOTHING = invocation -> null;

	/**
	 * Changes when told to
	 */
	private static class Switch implements RouteDeclarations.Changes {

		final AtomicBoolean changed = new AtomicBoolean();
		final List<Class<?>> watched = new ArrayList<>();

		@Override
		public void watch( final Class<?> type ) {
			watched.add( type );
		}

		@Override
		public boolean changed() {
			return changed.get();
		}

		@Override
		public void built() {
			changed.set( false );
		}
	}

	record Holder( int generation, PlainRoute about ) {}

	public record Page( Integer page ) {}

	@Test
	public void routesAreDeclaredAgainWhenTheirClassesChange() {
		final Switch changes = new Switch();
		final RouteDeclarations declarations = new RouteDeclarations( ERXRouter::new, changes );
		final AtomicInteger runs = new AtomicInteger();
		final List<String> order = new ArrayList<>();

		final Declared<Holder> app = declarations.declare( router -> {
			order.add( "app" );
			router.application().route( "/pages", Page.class, ( p, invocation ) -> null );
			return new Holder( runs.incrementAndGet(), router.application().map( "/about", NOTHING ) );
		} );
		final Declared<String> plugin = declarations.declare( router -> {
			order.add( "plugin" );

			// A declaration asking for the default router gets the one it's declaring into
			assertSame( router, ERXRouter.defaultRouter() );
			router.table( "plugin" ).map( "/plugin", NOTHING );
			return "plugin";
		} );

		final ERXRouter first = declarations.router();
		assertEquals( 1, app.get().generation() );
		assertTrue( changes.watched.contains( Holder.class ) && changes.watched.contains( Page.class ) );

		// Unchanged, nothing runs
		declarations.declareAgainIfChanged();
		assertSame( first, declarations.router() );

		changes.changed.set( true );
		declarations.declareAgainIfChanged();

		assertNotSame( first, declarations.router() );
		assertEquals( 2, app.get().generation() );
		assertEquals( List.of( "app", "plugin", "app", "plugin" ), order );
		assertEquals( 3, declarations.router().routes().size() );
		assertEquals( "plugin", plugin.get() );
	}

	@Test
	public void aDeclarationThatFailsLeavesTheCurrentRoutes() {
		final Switch changes = new Switch();
		final RouteDeclarations declarations = new RouteDeclarations( ERXRouter::new, changes );
		final AtomicBoolean broken = new AtomicBoolean();

		final Declared<String> app = declarations.declare( router -> {
			router.application().map( "/about", NOTHING );

			if( broken.get() ) {
				router.application().map( "/about", NOTHING );
			}

			return "routes";
		} );

		final ERXRouter first = declarations.router();
		broken.set( true );
		changes.changed.set( true );

		// The current routes stay, and requests say why until the routes are declared again
		final IllegalStateException e = assertThrows( IllegalStateException.class, declarations::declareAgainIfChanged );
		assertTrue( e.getMessage().contains( "conflicts" ), e.getMessage() );
		assertSame( first, declarations.router() );
		assertThrows( IllegalStateException.class, declarations::declareAgainIfChanged );

		broken.set( false );
		changes.changed.set( true );
		declarations.declareAgainIfChanged();
		assertNotSame( first, declarations.router() );
		assertEquals( "routes", app.get() );
	}

	@Test
	public void aJoinNeverNamedFailsADeclarationAgain() {
		final Switch changes = new Switch();
		final RouteDeclarations declarations = new RouteDeclarations( ERXRouter::new, changes );
		final AtomicBoolean named = new AtomicBoolean( true );

		declarations.declare( router -> {
			router.table( "plugin" ).join( "club", club -> club.map( "/guestbook", NOTHING ) );

			if( named.get() ) {
				router.application().group( "/club" ).named( "club" );
			}

			return "routes";
		} );

		named.set( false );
		changes.changed.set( true );
		assertThrows( IllegalStateException.class, declarations::declareAgainIfChanged );
	}

	@Test
	public void aRouteMappedOutsideADeclarationStopsDeclaringAgain() {
		final Switch changes = new Switch();
		final RouteDeclarations declarations = new RouteDeclarations( ERXRouter::new, changes );
		declarations.declare( router -> router.application().map( "/declared", NOTHING ) );

		// Mapped straight into the router: declaring the routes again would lose it, so they aren't
		declarations.router().application().map( "/undeclared", NOTHING );
		final ERXRouter router = declarations.router();
		changes.changed.set( true );
		declarations.declareAgainIfChanged();

		assertSame( router, declarations.router() );
		assertEquals( "the route /undeclared", router.undeclared() );
	}

	@Test
	public void aConverterRegisteredOutsideADeclarationStopsDeclaringAgain() {
		final Switch changes = new Switch();
		final RouteDeclarations declarations = new RouteDeclarations( ERXRouter::new, changes );

		// Registered by a declaration, it's registered again with it
		declarations.declare( router -> {
			router.converters().register( Page.class, er.routing.core.Converters.Converter.of( s -> new Page( 1 ), p -> "1" ) );
			return "routes";
		} );
		assertEquals( null, declarations.router().undeclared() );

		declarations.router().converters().register( Holder.class, er.routing.core.Converters.Converter.of( s -> null, h -> "" ) );
		assertEquals( "the converter for " + Holder.class.getName(), declarations.router().undeclared() );
	}
}
