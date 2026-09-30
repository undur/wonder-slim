package er.extensions.projectlayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.jupiter.api.Test;

public class ERXProjectLayoutTest {

	private static Properties properties( final String... keysAndValues ) {
		final Properties properties = new Properties();

		for( int i = 0; i < keysAndValues.length; i += 2 ) {
			properties.setProperty( keysAndValues[i], keysAndValues[i + 1] );
		}

		return properties;
	}

	@Test
	public void aProjectIsNamedAndTyped() {
		assertTrue( ERXProjectLayout.isProject( properties( "project.name", "App", "project.type", "application" ) ) );
		assertFalse( ERXProjectLayout.isProject( properties( "project.name", "App" ) ) );
		assertFalse( ERXProjectLayout.isProject( properties( "project.type", "application" ) ) );
		assertFalse( ERXProjectLayout.isProject( properties( "project.name", " ", "project.type", "application" ) ) );
		assertFalse( ERXProjectLayout.isProject( properties( "bin.includes", "META-INF/" ) ) );
	}

	@Test
	public void noKeysMeansTheMavenLayout() {
		final ERXProjectLayout layout = ERXProjectLayout.fromBuildProperties( properties( "project.name", "App", "classes.dir", "target/classes" ) );
		assertEquals( ERXProjectLayout.DEFAULT_COMPONENTS, layout.components() );
		assertEquals( ERXProjectLayout.DEFAULT_WORESOURCES, layout.woresources() );
		assertEquals( ERXProjectLayout.DEFAULT_WEBSERVER_RESOURCES, layout.webserverResources() );
	}

	@Test
	public void undeclaredKeysTakeTheMavenDefaults() {
		final ERXProjectLayout layout = ERXProjectLayout.fromBuildProperties( properties( ERXProjectLayout.COMPONENTS_KEY, "Components" ) );
		assertEquals( "Components", layout.components() );
		assertEquals( ERXProjectLayout.DEFAULT_WORESOURCES, layout.woresources() );
		assertEquals( ERXProjectLayout.DEFAULT_WEBSERVER_RESOURCES, layout.webserverResources() );
	}

	@Test
	public void pathsAreTakenAsWritten() {
		final ERXProjectLayout layout = ERXProjectLayout.fromBuildProperties( properties( ERXProjectLayout.COMPONENTS_KEY, "./Components/" ) );
		assertEquals( "./Components/", layout.components() );
	}

	@Test
	public void emptyValuesAreRefused() {
		assertThrows( IllegalArgumentException.class, () -> ERXProjectLayout.fromBuildProperties( properties( ERXProjectLayout.WORESOURCES_KEY, "" ) ) );
	}

	@Test
	public void factoryIsAddedWithoutDisplacingOthers() {
		final String ours = ERXProjectLayout.FACTORY_CLASS_NAME;
		assertEquals( ours, ERXProjectLayout.factoriesWithOurs( null ) );
		assertEquals( ours, ERXProjectLayout.factoriesWithOurs( " " ) );
		assertEquals( ours + ",a.Factory", ERXProjectLayout.factoriesWithOurs( "a.Factory" ) );
		assertEquals( ours + ",a.Factory, b.Factory", ERXProjectLayout.factoriesWithOurs( "a.Factory, b.Factory" ) );
		assertEquals( "(" + ours + ", a.Factory, b.Factory)", ERXProjectLayout.factoriesWithOurs( "(a.Factory, b.Factory)" ) );
		assertEquals( "(" + ours + ")", ERXProjectLayout.factoriesWithOurs( "()" ) );
		assertEquals( ours + ",a.Factory", ERXProjectLayout.factoriesWithOurs( ours + ",a.Factory" ) );
	}
}
