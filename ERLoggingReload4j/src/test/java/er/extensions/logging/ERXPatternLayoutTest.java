package er.extensions.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.jupiter.api.Test;

/**
 * The layout's own conversions (%W application info, %V JVM memory). Without a running application, application info
 * falls back to "-" for every value.
 */
public class ERXPatternLayoutTest {

	private static String format( final String pattern ) {
		final LoggingEvent event = new LoggingEvent( Logger.class.getName(), Logger.getLogger( "test" ), Level.INFO, "message", null );
		return new ERXPatternLayout( pattern ).format( event );
	}

	@Test
	public void applicationInfoWithoutAnApplication() {
		assertEquals( "-[-:- -]", format( "%W{n[i:p s]}" ) );
		assertEquals( "-[-:- -] message", format( "%W{n[i:p s]} %m" ) );
	}

	@Test
	public void applicationInfoWithTheDefaultTemplate() {
		assertEquals( "-[-:- -]", format( "%W" ) );
	}

	@Test
	public void memory() {
		final String memory = format( "%V{u/f}" );
		assertTrue( memory.matches( "[\\d.]+ [A-Za-z]+/[\\d.]+ [A-Za-z]+" ), memory );

		final String labelled = format( "%V{u used/f free}" );
		assertTrue( labelled.matches( "[\\d.]+ [A-Za-z]+ used/[\\d.]+ [A-Za-z]+ free" ), labelled );

		final String all = format( "%V{t,u,f,m}" );
		assertTrue( all.matches( "[\\d.]+ [A-Za-z]+,[\\d.]+ [A-Za-z]+,[\\d.]+ [A-Za-z]+,[\\d.]+ [A-Za-z]+" ), all );
	}

	@Test
	public void unknownLettersStayAsWritten() {
		final String memory = format( "%V{u x}" );
		assertTrue( memory.matches( "[\\d.]+ [A-Za-z]+ x" ), memory );
	}

	@Test
	public void fillTemplate() {
		assertEquals( "MyApp[42:2001 3]", ERXPatternParser.fillTemplate( "@@appName@@[@@pid@@:@@portNumber@@ @@sessionCount@@]", java.util.Map.of( "appName", "MyApp", "pid", "42", "portNumber", "2001", "sessionCount", "3" ) ) );
		assertEquals( "MyApp[-:- -]", ERXPatternParser.fillTemplate( "@@appName@@[@@pid@@:@@portNumber@@ @@sessionCount@@]", java.util.Map.of( "appName", "MyApp" ) ) );
		assertEquals( "cost $5 \\ ok", ERXPatternParser.fillTemplate( "cost @@price@@ ok", java.util.Map.of( "price", "$5 \\" ) ) );
		assertEquals( "no placeholders", ERXPatternParser.fillTemplate( "no placeholders", java.util.Map.of() ) );
	}
}
