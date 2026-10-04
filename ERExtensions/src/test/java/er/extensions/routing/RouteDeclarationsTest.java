package er.extensions.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import er.routing.conversion.Converters;


/**
 * Route constants given patterns by declarations, and declared again when their classes change: a new router, the
 * constants following it, or the current ones kept if a declaration fails
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

	public record Page( Integer page ) {}

	public interface Routes {
		Route<Page> pages = Route.of( Page.class );
		PlainRoute about = Route.plain();
	}

	/**
	 * Declarations checking only these constants, not every test's
	 */
	private static RouteDeclarations declarations( final Switch changes, final RouteIdentity<?>... constants ) {
		return new RouteDeclarations( ERXRouter::new, changes, router -> List.of( constants ).stream().filter( c -> router.binding( c ) == null ).<RouteIdentity<?>>map( c -> c ).toList() );
	}

	@Test
	public void aConstantFollowsTheRoutesAsTheyreDeclaredAgain() {
		final Switch changes = new Switch();
		final RouteDeclarations declarations = declarations( changes, Routes.pages, Routes.about );
		final AtomicReference<String> pagesAt = new AtomicReference<>( "/pages" );
		final List<String> order = new ArrayList<>();

		declarations.declare( router -> {
			order.add( "app" );
			router.application().route( pagesAt.get(), Routes.pages, ( p, invocation ) -> null );
			router.application().map( "/about", Routes.about, NOTHING );
		}, RouteDeclarationsTest.class );
		declarations.declare( router -> {
			order.add( "plugin" );

			// A declaration asking for the default router gets the one it's declaring into
			assertSame( router, ERXRouter.defaultRouter() );
			router.table( "plugin" ).map( "/plugin", NOTHING );
		}, RouteDeclarationsTest.class );

		final ERXRouter first = declarations.router();
		assertEquals( "/pages", Routes.pages.pattern() );
		assertTrue( changes.watched.contains( Routes.class ) && changes.watched.contains( Page.class ) );

		// Unchanged, nothing runs
		declarations.declareAgainIfChanged();
		assertSame( first, declarations.router() );

		pagesAt.set( "/pages/all" );
		changes.changed.set( true );
		declarations.declareAgainIfChanged();

		assertNotSame( first, declarations.router() );
		assertEquals( "/pages/all", Routes.pages.pattern() );
		assertEquals( List.of( "app", "plugin", "app", "plugin" ), order );
		assertEquals( 3, declarations.router().routes().size() );
	}

	@Test
	public void aDeclarationThatFailsLeavesTheCurrentRoutes() {
		final Switch changes = new Switch();
		final RouteDeclarations declarations = declarations( changes, Routes.about );
		final AtomicBoolean broken = new AtomicBoolean();

		declarations.declare( router -> {
			router.application().map( "/about", Routes.about, NOTHING );

			if( broken.get() ) {
				router.application().map( "/about", NOTHING );
			}
		}, null );

		final ERXRouter first = declarations.router();
		broken.set( true );
		changes.changed.set( true );

		// The current routes stay, the constant keeps its pattern, and requests say why until the routes are declared again
		final IllegalStateException e = assertThrows( IllegalStateException.class, declarations::declareAgainIfChanged );
		assertTrue( e.getMessage().contains( "conflicts" ), e.getMessage() );
		assertSame( first, declarations.router() );
		assertEquals( "/about", Routes.about.pattern() );
		assertThrows( IllegalStateException.class, declarations::declareAgainIfChanged );

		broken.set( false );
		changes.changed.set( true );
		declarations.declareAgainIfChanged();
		assertNotSame( first, declarations.router() );
	}

	@Test
	public void aConstantNoDeclarationGivesAPatternFails() {
		final Switch changes = new Switch();
		final RouteDeclarations declarations = declarations( changes, Routes.about );
		declarations.declare( router -> router.application().map( "/elsewhere", NOTHING ), null );

		final IllegalStateException e = assertThrows( IllegalStateException.class, declarations::checkDeclared );
		assertTrue( e.getMessage().contains( "Routes.about" ), e.getMessage() );
	}

	@Test
	public void aConstantDeclaredTwiceFails() {
		final RouteDeclarations declarations = declarations( new Switch() );

		assertThrows( IllegalArgumentException.class, () -> declarations.declare( router -> {
			router.application().map( "/a", Routes.about, NOTHING );
			router.application().map( "/b", Routes.about, NOTHING );
		}, null ) );
	}

	@Test
	public void aJoinNeverNamedFailsADeclarationAgain() {
		final Switch changes = new Switch();
		final RouteDeclarations declarations = declarations( changes );
		final AtomicBoolean named = new AtomicBoolean( true );

		declarations.declare( router -> {
			router.table( "plugin" ).join( "club", club -> club.map( "/guestbook", NOTHING ) );

			if( named.get() ) {
				router.application().group( "/club" ).named( "club" );
			}
		}, null );

		named.set( false );
		changes.changed.set( true );
		assertThrows( IllegalStateException.class, declarations::declareAgainIfChanged );
	}

	@Test
	public void changingTheRoutesOutsideADeclarationFails() {
		final RouteDeclarations declarations = declarations( new Switch() );
		final AtomicReference<RouteGroup> admin = new AtomicReference<>();
		final AtomicReference<RouteGroup> plugin = new AtomicReference<>();
		declarations.declare( router -> {
			admin.set( router.application().group( "/admin" ).named( "admin" ) );
			plugin.set( router.table( "plugin" ) );
			router.converters().register( Page.class, er.routing.conversion.Converters.Converter.of( s -> new Page( 1 ), p -> "1" ) );
		}, null );

		final ERXRouter router = declarations.router();

		// A plugin's login filter added from its own startup code would be lost when the routes are declared again
		final IllegalStateException e = assertThrows( IllegalStateException.class, () -> admin.get().wrap( ( invocation, next ) -> next.handle( invocation ) ) );
		assertTrue( e.getMessage().contains( "ERXRouter.declare" ), e.getMessage() );

		assertThrows( IllegalStateException.class, () -> router.application() );
		assertThrows( IllegalStateException.class, () -> router.table( "other" ) );
		assertThrows( IllegalStateException.class, () -> admin.get().map( "/x", NOTHING ) );
		assertThrows( IllegalStateException.class, () -> plugin.get().join( "admin", group -> {} ) );
		assertThrows( IllegalStateException.class, () -> router.converters().register( RouteDeclarationsTest.class, er.routing.conversion.Converters.Converter.of( s -> null, t -> "" ) ) );

		// Reading is fine
		assertEquals( 0, router.routes().size() );
		assertTrue( router.converters().converts( Page.class ) );
	}

	public interface Optional {
		PlainRoute devTools = Route.plain().optional();
	}

	@Test
	public void anOptionalConstantMayGoWithoutAPattern() {
		final RouteDeclarations declarations = new RouteDeclarations( ERXRouter::new, new Switch() );
		declarations.declare( router -> router.application().map( "/elsewhere", NOTHING ), null );

		assertTrue( RouteIdentity.undeclaredIn( declarations.router() ).stream().noneMatch( identity -> identity == Optional.devTools ) );
	}

	@Test
	public void aRedirectsParametersAreTheRoutes() {
		final RouteDeclarations declarations = declarations( new Switch(), Routes.about );

		// The old URL has {id}, which /about doesn't take: refused once the routes are declared
		assertThrows( IllegalArgumentException.class, () -> declarations.declare( router -> {
			router.application().map( "/about", Routes.about, NOTHING );
			router.application().redirect( "/old/{id}", Routes.about );
			router.checkJoins();
		}, null ) );
	}
}
