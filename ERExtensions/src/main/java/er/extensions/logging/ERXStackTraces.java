package er.extensions.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import er.extensions.ERXP;
import er.extensions.foundation.ERXExceptionUtilities;
import er.extensions.foundation.ERXProperties;

/**
 * How a logged throwable's stack trace is written, the same with every logging backend: the console logger, and
 * ERLoggingReload4j and ERLoggingLogback through their backends.
 *
 * A throwable that only wraps another ({@code NSForwardException}, {@code InvocationTargetException}) is printed as
 * the throwable it wraps ({@code er.logging.stackTrace.unwrap}, default true), and optionally only the innermost cause
 * of the chain is printed ({@code er.logging.stackTrace.innermostOnly}, default false).
 *
 * The trace is the JDK's own, unless {@code er.logging.stackTrace.skipFrames} is true: then runs of frames that are
 * plumbing rather than code anyone acts on (reflection, the element tree WebObjects walks for every request, the
 * server's threads) are each collapsed into one line. {@code er.logging.stackTrace.skipPatterns} replaces the list of
 * those frames with regular expressions of its own, comma separated, matched against {@code class.method}. The first
 * frame of each throwable is always printed.
 */
public final class ERXStackTraces {

	/**
	 * The frames skipped when skipping is on and no patterns are configured: the plumbing between the frames that say what
	 * happened. Reflection and method handles, key-value coding, the element tree WebObjects walks (but not the elements
	 * themselves, which say where in the template), Parsley's proxies, the request's way from the worker thread to the
	 * page, and the server's threads.
	 */
	static final List<String> DEFAULT_SKIP_PATTERNS = List.of(
			// Reflection, method handles, threads
			"java\\.lang\\.reflect\\..*",
			"java\\.lang\\.invoke\\..*",
			"jdk\\.internal\\.reflect\\..*",
			"jdk\\.internal\\.vm\\..*",
			"java\\.lang\\.Thread\\.run.*",
			"java\\.lang\\.VirtualThread\\..*",
			"java\\.util\\.concurrent\\..*",
			"org\\.eclipse\\.jetty\\..*",

			// Key-value coding
			"com\\.webobjects\\.foundation\\.NSSelector.*",
			"com\\.webobjects\\.foundation\\.NSKeyValueCoding.*",
			"com\\.webobjects\\.foundation\\._NSUtilities\\..*",
			"er\\.extensions\\.ERXKVCReflectionHack.*",
			"com\\.webobjects\\.appserver\\._private\\.WOKeyValueAssociation\\..*",
			"com\\.webobjects\\.appserver\\.WOComponent\\.(valueForKey|valueForKeyPath|takeValueForKey|takeValueForKeyPath)",

			// The element tree
			"com\\.webobjects\\.appserver\\._private\\.WODynamicGroup\\..*",
			"com\\.webobjects\\.appserver\\._private\\.WOComponentReference\\..*",
			"com\\.webobjects\\.appserver\\._private\\.WOComponentContent\\..*",
			"com\\.webobjects\\.appserver\\.WOComponent\\.(appendToResponse|invokeAction|takeValuesFromRequest|_.*)",
			"parsley\\.ParsleyProxyAssociation\\..*",
			"parsley\\.ParsleyProxyElement\\..*",
			"parsley\\.ParsleyTemplateRootElement\\..*",

			// From the worker thread to the page
			"com\\.webobjects\\.appserver\\._private\\.WOWorkerThread\\..*",
			"com\\.webobjects\\.appserver\\._private\\.WO(Component|Action)RequestHandler\\..*",
			"com\\.webobjects\\.appserver\\.WOApplication\\.(dispatchRequest|invokeAction|appendToResponse|takeValuesFromRequest)",
			"com\\.webobjects\\.appserver\\.WOSession\\.(invokeAction|appendToResponse|takeValuesFromRequest)",
			"com\\.webobjects\\.appserver\\.WODirectAction\\.performActionNamed",
			"er\\.extensions\\.appserver\\.ERXApplication\\.dispatchRequest.*",
			"er\\.extensions\\.appserver\\.ajax\\.ERXAjaxApplication\\.(dispatchRequest|invokeAction|appendToResponse|takeValuesFromRequest)",
			"er\\.extensions\\.appserver\\.ERXComponentActionRequestHandler\\..*",
			"er\\.extensions\\.appserver\\.ERXDirectActionRequestHandler\\..*" );

	/**
	 * Runs of skippable frames shorter than this are written out: a line saying one frame was skipped says no less
	 */
	private static final int SHORTEST_RUN_SKIPPED = 2;

	/**
	 * The patterns last compiled, and the property value they were compiled from
	 */
	private static volatile CompiledPatterns _compiled = new CompiledPatterns( null, compile( DEFAULT_SKIP_PATTERNS ) );

	private record CompiledPatterns( String source, List<Pattern> patterns ) {}

	private ERXStackTraces() {}

	/**
	 * @return The throwable to print for the given one, as the properties say
	 */
	public static Throwable throwableToPrint( final Throwable throwable ) {
		Throwable result = throwable;

		if( ERXProperties.booleanForKeyWithDefault( ERXP.LOGGING_STACK_TRACE_UNWRAP.id(), true ) ) {
			final Throwable unwrapped = ERXExceptionUtilities.getMeaningfulThrowable( result );

			if( unwrapped != null ) {
				result = unwrapped;
			}
		}

		if( ERXProperties.booleanForKeyWithDefault( ERXP.LOGGING_STACK_TRACE_INNERMOST_ONLY.id(), false ) ) {
			while( result.getCause() != null && result.getCause() != result ) {
				result = result.getCause();
			}
		}

		return result;
	}

	/**
	 * @return The stack trace of the given throwable as it's written to the log, ending with a line separator
	 */
	public static String format( final Throwable throwable ) {
		final Throwable toPrint = throwableToPrint( throwable );

		if( !ERXProperties.booleanForKeyWithDefault( ERXP.LOGGING_STACK_TRACE_SKIP_FRAMES.id(), false ) ) {
			return jdkTrace( toPrint );
		}

		final List<Pattern> patterns;

		try {
			patterns = skipPatterns();
		}
		catch( final PatternSyntaxException e ) {
			// The trace being logged matters more than the setting: written in full, with the problem named above it
			return "(" + ERXP.LOGGING_STACK_TRACE_SKIP_PATTERNS.id() + " is not a valid pattern, so no frames are skipped: " + e.getDescription() + ": " + e.getPattern() + ")" + System.lineSeparator() + jdkTrace( toPrint );
		}

		final StringBuilder trace = new StringBuilder();
		final Set<Throwable> printed = Collections.newSetFromMap( new IdentityHashMap<>() );
		printed.add( toPrint );
		trace.append( toPrint ).append( System.lineSeparator() );
		appendFrames( trace, toPrint.getStackTrace(), toPrint.getStackTrace().length, "", patterns );
		appendEnclosed( trace, toPrint, "", printed, patterns );
		return trace.toString();
	}

	/**
	 * @return The trace exactly as the JDK prints it
	 */
	private static String jdkTrace( final Throwable throwable ) {
		final StringWriter trace = new StringWriter();
		throwable.printStackTrace( new PrintWriter( trace ) );
		return trace.toString();
	}

	/**
	 * Appends the suppressed throwables and the cause of the given one, as the JDK does
	 */
	private static void appendEnclosed( final StringBuilder trace, final Throwable throwable, final String prefix, final Set<Throwable> printed, final List<Pattern> patterns ) {
		for( final Throwable suppressed : throwable.getSuppressed() ) {
			appendEnclosed( trace, suppressed, throwable.getStackTrace(), "Suppressed: ", prefix + "\t", printed, patterns );
		}

		if( throwable.getCause() != null ) {
			appendEnclosed( trace, throwable.getCause(), throwable.getStackTrace(), "Caused by: ", prefix, printed, patterns );
		}
	}

	/**
	 * Appends a suppressed throwable or a cause, leaving out the frames it has in common with the throwable enclosing it
	 */
	private static void appendEnclosed( final StringBuilder trace, final Throwable throwable, final StackTraceElement[] enclosingTrace, final String caption, final String prefix, final Set<Throwable> printed, final List<Pattern> patterns ) {
		if( !printed.add( throwable ) ) {
			trace.append( prefix ).append( caption ).append( "[CIRCULAR REFERENCE: " ).append( throwable ).append( "]" ).append( System.lineSeparator() );
			return;
		}

		final StackTraceElement[] frames = throwable.getStackTrace();
		int last = frames.length - 1;

		for( int enclosing = enclosingTrace.length - 1; last >= 0 && enclosing >= 0 && frames[last].equals( enclosingTrace[enclosing] ); enclosing-- ) {
			last--;
		}

		final int framesInCommon = frames.length - 1 - last;

		trace.append( prefix ).append( caption ).append( throwable ).append( System.lineSeparator() );
		appendFrames( trace, frames, last + 1, prefix, patterns );

		if( framesInCommon != 0 ) {
			trace.append( prefix ).append( "\t... " ).append( framesInCommon ).append( " more" ).append( System.lineSeparator() );
		}

		appendEnclosed( trace, throwable, prefix, printed, patterns );
	}

	/**
	 * Appends the first {@code count} frames, each run of skipped frames after the first collapsed into one line
	 */
	private static void appendFrames( final StringBuilder trace, final StackTraceElement[] frames, final int count, final String prefix, final List<Pattern> patterns ) {
		int runStart = -1;

		for( int i = 0; i < count; i++ ) {
			if( i > 0 && isSkipped( frames[i], patterns ) ) {
				if( runStart < 0 ) {
					runStart = i;
				}

				continue;
			}

			appendRun( trace, frames, runStart, i, prefix );
			runStart = -1;
			appendFrame( trace, frames[i], prefix );
		}

		appendRun( trace, frames, runStart, count, prefix );
	}

	/**
	 * Appends the run of skippable frames from {@code start} to {@code end}: collapsed into one line if it's long enough,
	 * otherwise written out. Nothing if there's no run ({@code start} negative).
	 */
	private static void appendRun( final StringBuilder trace, final StackTraceElement[] frames, final int start, final int end, final String prefix ) {
		if( start < 0 ) {
			return;
		}

		if( end - start < SHORTEST_RUN_SKIPPED ) {
			for( int i = start; i < end; i++ ) {
				appendFrame( trace, frames[i], prefix );
			}
		}
		else {
			trace.append( prefix ).append( "\t... " ).append( end - start ).append( " frames skipped" ).append( System.lineSeparator() );
		}
	}

	private static void appendFrame( final StringBuilder trace, final StackTraceElement frame, final String prefix ) {
		trace.append( prefix ).append( "\tat " ).append( frame ).append( System.lineSeparator() );
	}

	private static boolean isSkipped( final StackTraceElement frame, final List<Pattern> patterns ) {
		final String name = frame.getClassName() + "." + frame.getMethodName();

		for( final Pattern pattern : patterns ) {
			if( pattern.matcher( name ).matches() ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * @return The patterns of the frames to skip: the configured ones, compiled again only when they change, or the defaults
	 */
	static List<Pattern> skipPatterns() {
		final String configured = ERXProperties.stringForKey( ERXP.LOGGING_STACK_TRACE_SKIP_PATTERNS.id() );
		final CompiledPatterns compiled = _compiled;

		if( Objects.equals( configured, compiled.source() ) ) {
			return compiled.patterns();
		}

		final List<String> sources = configured == null || configured.isBlank() ? DEFAULT_SKIP_PATTERNS : splitPatterns( configured );
		final CompiledPatterns recompiled = new CompiledPatterns( configured, compile( sources ) );
		_compiled = recompiled;
		return recompiled.patterns();
	}

	private static List<String> splitPatterns( final String configured ) {
		final List<String> result = new ArrayList<>();

		for( final String part : configured.split( "," ) ) {
			if( !part.isBlank() ) {
				result.add( part.strip() );
			}
		}

		return result;
	}

	private static List<Pattern> compile( final List<String> sources ) {
		return sources.stream().map( Pattern::compile ).toList();
	}
}
