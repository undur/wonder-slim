package er.extensions.logging;

import java.io.PrintWriter;
import java.io.StringWriter;

import er.extensions.ERXP;
import er.extensions.foundation.ERXExceptionUtilities;
import er.extensions.foundation.ERXProperties;

/**
 * How a logged throwable's stack trace is written, the same with every logging backend: the console logger, and
 * ERLoggingReload4j and ERLoggingLogback through their backends.
 *
 * A throwable that only wraps another ({@code NSForwardException}, {@code InvocationTargetException}) is printed as
 * the throwable it wraps ({@code er.logging.stackTrace.unwrap}, default true), and optionally only the innermost cause
 * of the chain is printed ({@code er.logging.stackTrace.innermostOnly}, default false). Otherwise the trace is the
 * JDK's own.
 */
public final class ERXStackTraces {

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
		final StringWriter trace = new StringWriter();
		throwableToPrint( throwable ).printStackTrace( new PrintWriter( trace ) );
		return trace.toString();
	}
}
