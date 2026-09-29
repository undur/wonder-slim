package er.extensions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class ERXPluginsTest {

	public static class Base implements ERXPlugin {}

	public static class Alpha implements ERXPlugin {
		@Override
		public List<Class<? extends ERXPlugin>> requires() {
			return List.of( Base.class );
		}
	}

	public static class Beta implements ERXPlugin {
		@Override
		public List<Class<? extends ERXPlugin>> requires() {
			return List.of( Base.class );
		}
	}

	/**
	 * Named to sort before the others, so the order it lands in comes from what it requires
	 */
	public static class AaTop implements ERXPlugin {
		@Override
		public List<Class<? extends ERXPlugin>> requires() {
			return List.of( Beta.class, Alpha.class );
		}
	}

	public static class Unrelated implements ERXPlugin {}

	public static class CycleA implements ERXPlugin {
		@Override
		public List<Class<? extends ERXPlugin>> requires() {
			return List.of( CycleB.class );
		}
	}

	public static class CycleB implements ERXPlugin {
		@Override
		public List<Class<? extends ERXPlugin>> requires() {
			return List.of( CycleA.class );
		}
	}

	private static List<String> names( final List<ERXPlugin> plugins ) {
		return plugins.stream().map( plugin -> plugin.getClass().getSimpleName() ).toList();
	}

	@Test
	public void aPluginComesAfterThePluginsItRequires() {
		final List<ERXPlugin> ordered = ERXPlugins.ordered( List.of( new AaTop(), new Beta(), new Alpha(), new Base() ) );
		assertEquals( List.of( "Base", "Alpha", "Beta", "AaTop" ), names( ordered ) );
	}

	@Test
	public void theOrderDoesNotDependOnTheOrderFound() {
		final List<String> one = names( ERXPlugins.ordered( List.of( new Unrelated(), new Base(), new Beta(), new Alpha() ) ) );
		final List<String> other = names( ERXPlugins.ordered( List.of( new Alpha(), new Beta(), new Base(), new Unrelated() ) ) );
		assertEquals( one, other );
	}

	@Test
	public void aMissingRequiredPluginStopsTheLaunch() {
		final IllegalStateException e = assertThrows( IllegalStateException.class, () -> ERXPlugins.ordered( List.of( new Alpha() ) ) );
		assertTrue( e.getMessage().contains( Base.class.getName() ), e.getMessage() );
	}

	@Test
	public void aCycleStopsTheLaunchAndNamesItsPlugins() {
		final IllegalStateException e = assertThrows( IllegalStateException.class, () -> ERXPlugins.ordered( List.of( new Base(), new CycleA(), new CycleB() ) ) );
		assertTrue( e.getMessage().contains( CycleA.class.getName() ) && e.getMessage().contains( CycleB.class.getName() ), e.getMessage() );
		assertTrue( !e.getMessage().contains( Base.class.getName() ), "Only the plugins in the cycle are named: " + e.getMessage() );
	}

	@Test
	public void aPluginListedTwiceStopsTheLaunch() {
		assertThrows( IllegalStateException.class, () -> ERXPlugins.ordered( List.of( new Base(), new Base() ) ) );
	}
}
