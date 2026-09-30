package er.extensions.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		System.clearProperty( ERXP.LOGGING_STACK_TRACE_SKIP_FRAMES.id() );
		System.clearProperty( ERXP.LOGGING_STACK_TRACE_SKIP_PATTERNS.id() );
	}

	/**
	 * @return The trace exactly as the JDK prints it
	 */
	private static String jdkTrace( final Throwable throwable ) {
		final java.io.StringWriter trace = new java.io.StringWriter();
		throwable.printStackTrace( new java.io.PrintWriter( trace ) );
		return trace.toString();
	}

	/**
	 * @return An exception with a cause and a suppressed exception, each created a few calls deeper
	 */
	private static Throwable chain() {
		final IllegalStateException outer = new IllegalStateException( "outer", deeper( 3 ) );
		outer.addSuppressed( new IllegalArgumentException( "suppressed" ) );
		return outer;
	}

	private static Throwable deeper( final int depth ) {
		return depth == 0 ? new RuntimeException( "cause" ) : deeper( depth - 1 );
	}

	/**
	 * @return An exception thrown from a method invoked through reflection, unwrapped from its InvocationTargetException
	 */
	private static Throwable throughReflection() throws Exception {
		try {
			ERXStackTracesTest.class.getDeclaredMethod( "fail" ).invoke( null );
			throw new AssertionError( "fail() didn't throw" );
		}
		catch( final java.lang.reflect.InvocationTargetException e ) {
			return e;
		}
	}

	@SuppressWarnings("unused")
	private static void fail() {
		throw new IllegalStateException( "thrown through reflection" );
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

	@Test
	public void framesAreNotSkippedByDefault() {
		final Throwable chain = chain();
		assertEquals( jdkTrace( chain ), ERXStackTraces.format( chain ) );
	}

	@Test
	public void skippingNothingGivesTheJdkTraceExactly() {
		System.setProperty( ERXP.LOGGING_STACK_TRACE_SKIP_FRAMES.id(), "true" );
		System.setProperty( ERXP.LOGGING_STACK_TRACE_SKIP_PATTERNS.id(), "nothing\\.matches\\.this" );
		final Throwable chain = chain();

		assertEquals( jdkTrace( chain ), ERXStackTraces.format( chain ), "Causes, suppressed exceptions and frames in common are laid out as the JDK does" );
	}

	@Test
	public void reflectionFramesAreCollapsed() throws Exception {
		System.setProperty( ERXP.LOGGING_STACK_TRACE_SKIP_FRAMES.id(), "true" );
		final String trace = ERXStackTraces.format( throughReflection() );

		assertTrue( trace.startsWith( "java.lang.IllegalStateException: thrown through reflection" ), trace );
		assertTrue( trace.contains( "at er.extensions.logging.ERXStackTracesTest.fail(" ), "The frame that threw" );
		assertTrue( trace.contains( "at er.extensions.logging.ERXStackTracesTest.throughReflection(" ), "The code that called it" );
		assertFalse( trace.contains( "jdk.internal.reflect" ) || trace.contains( "java.lang.reflect.Method.invoke" ), trace );
		assertTrue( trace.matches( "(?s).*\\.\\.\\. \\d+ frames? skipped.*" ), trace );
	}

	@Test
	public void theFirstFrameIsAlwaysWritten() {
		System.setProperty( ERXP.LOGGING_STACK_TRACE_SKIP_FRAMES.id(), "true" );
		System.setProperty( ERXP.LOGGING_STACK_TRACE_SKIP_PATTERNS.id(), ".*" );
		final Throwable throwable = new IllegalStateException( "everything matches" );
		final String[] lines = ERXStackTraces.format( throwable ).split( "\\R" );

		assertEquals( "java.lang.IllegalStateException: everything matches", lines[0] );
		assertEquals( "\tat " + throwable.getStackTrace()[0], lines[1] );
		assertEquals( "\t... " + (throwable.getStackTrace().length - 1) + " frames skipped", lines[2] );
		assertEquals( 3, lines.length );
	}

	@Test
	public void anInvalidPatternWritesTheWholeTrace() {
		System.setProperty( ERXP.LOGGING_STACK_TRACE_SKIP_FRAMES.id(), "true" );
		System.setProperty( ERXP.LOGGING_STACK_TRACE_SKIP_PATTERNS.id(), "unclosed(" );
		final Throwable throwable = new IllegalStateException( "invalid pattern" );
		final String trace = ERXStackTraces.format( throwable );

		assertTrue( trace.startsWith( "(er.logging.stackTrace.skipPatterns is not a valid pattern" ), trace );
		assertTrue( trace.endsWith( jdkTrace( throwable ) ) );
	}

	@Test
	public void aSingleSkippableFrameIsWrittenOut() throws Exception {
		System.setProperty( ERXP.LOGGING_STACK_TRACE_SKIP_FRAMES.id(), "true" );
		System.setProperty( ERXP.LOGGING_STACK_TRACE_SKIP_PATTERNS.id(), "java\\.lang\\.reflect\\.Method\\.invoke" );
		final String trace = ERXStackTraces.format( throughReflection() );

		assertTrue( trace.contains( "at java.base/java.lang.reflect.Method.invoke(" ), trace );
		assertFalse( trace.contains( "skipped" ), "A line saying one frame was skipped would say no less than the frame" );
	}
}
