package er.extensions.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.webobjects.foundation.NSForwardException;

import er.extensions.ERXP;

public class ERXStackTracesTest {

	@AfterEach
	public void clean() {
		System.clearProperty( ERXP.LOGGING_STACK_TRACE_UNWRAP.id() );
		System.clearProperty( ERXP.LOGGING_STACK_TRACE_INNERMOST_ONLY.id() );
	}

	/**
	 * @return An exception with a cause, wrapped the way WebObjects wraps one
	 */
	private static Throwable wrapped() {
		return new NSForwardException( new IllegalStateException( "outer", new IllegalArgumentException( "inner" ) ) );
	}

	@Test
	public void aWrapperIsPrintedAsTheThrowableItWraps() {
		final String trace = ERXStackTraces.format( wrapped() );

		assertTrue( trace.startsWith( "java.lang.IllegalStateException: outer" ), trace );
		assertTrue( trace.contains( "Caused by: java.lang.IllegalArgumentException: inner" ), "The causes are printed" );
		assertFalse( trace.contains( "NSForwardException" ) );
	}

	@Test
	public void unwrappingCanBeTurnedOff() {
		System.setProperty( ERXP.LOGGING_STACK_TRACE_UNWRAP.id(), "false" );

		assertTrue( ERXStackTraces.format( wrapped() ).startsWith( "com.webobjects.foundation.NSForwardException" ) );
	}

	@Test
	public void onlyTheInnermostCauseWhenAskedFor() {
		System.setProperty( ERXP.LOGGING_STACK_TRACE_INNERMOST_ONLY.id(), "true" );
		final String trace = ERXStackTraces.format( wrapped() );

		assertTrue( trace.startsWith( "java.lang.IllegalArgumentException: inner" ), trace );
		assertFalse( trace.contains( "Caused by" ) );
	}

	@Test
	public void theConsoleLoggerWritesTracesTheSameWay() {
		final ERXConsoleLayout.Event event = new ERXConsoleLayout.Event( "WARN", "a.b.C", "Failed", "main", java.time.LocalDateTime.now() );
		final String line = ERXConsoleLayout.of( "%m%n" ).format( event, wrapped() );

		assertTrue( line.startsWith( "Failed\njava.lang.IllegalStateException: outer" ), line );
	}
}
