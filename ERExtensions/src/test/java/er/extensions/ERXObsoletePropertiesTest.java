package er.extensions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class ERXObsoletePropertiesTest {

	private static final List<String> KEYS = List.of(
			"er.javamail.smtpHost",
			"er.javamail.smtpHost.newsletter",
			"er.extensions.ERXNSLogLog4jBridge",
			"er.extensions.ERXNSLogLog4jBridge.ignoreNSLogSettings",
			"com.webobjects.jdbcadaptor.PostgresqlExpression.enableIdentifierQuoting",
			"test.obsolete.unrelated" );

	@AfterEach
	public void clean() {
		KEYS.forEach( System::clearProperty );
	}

	/**
	 * @return The lines printObsoleteProperties() writes to the console: each reported property's name on a line of its own,
	 *         followed by an indented line explaining why
	 */
	private static List<String> report() {
		final ByteArrayOutputStream console = new ByteArrayOutputStream();
		final PrintStream originalOut = System.out;
		System.setOut( new PrintStream( console, true ) );

		try {
			ERXObsoleteProperties.printObsoleteProperties();
		}
		finally {
			System.setOut( originalOut );
		}

		return console.toString().lines().toList();
	}

	@Test
	public void keysOfRemovedFeaturesAreReported() {
		KEYS.forEach( key -> System.setProperty( key, "value" ) );
		final List<String> report = report();

		assertTrue( report.contains( "er.javamail.smtpHost" ), "A key ERJavaMail read" );
		assertTrue( report.contains( "er.javamail.smtpHost.newsletter" ), "A per-context key ERJavaMail built at runtime" );
		assertTrue( report.contains( "er.extensions.ERXNSLogLog4jBridge" ) );
		assertTrue( report.contains( "com.webobjects.jdbcadaptor.PostgresqlExpression.enableIdentifierQuoting" ) );

		assertFalse( report.contains( "er.extensions.ERXNSLogLog4jBridge.ignoreNSLogSettings" ), "Still read by ERXNSLogBridge" );
		assertFalse( report.contains( "test.obsolete.unrelated" ) );
	}
}
