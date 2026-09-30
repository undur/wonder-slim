package er.extensions.foundation;

import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.stream.Collectors;

import com.webobjects.foundation.NSForwardException;

/**
 * Provides a set of utilities for displaying and managing exceptions.
 * 
 * @author mschrag
 */

public class ERXExceptionUtilities {

	/**
	 * Implemented by exception classes that you explicitly want to not appear in stack dumps.
	 */
	private static interface WeDontNeedAStackTraceException {}

	/**
	 * @return The "meaningful" root cause from the given throwable. For instance, an InvocationTargetException is useless, it's the cause that matters.
	 */
	public static Throwable getMeaningfulThrowable(Throwable t) {
		Throwable meaningfulThrowable;

		if (t instanceof NSForwardException) {
			meaningfulThrowable = t.getCause();
		}
		else if (t instanceof InvocationTargetException ) {
			meaningfulThrowable = t.getCause();
		}
		else if (t instanceof WeDontNeedAStackTraceException && t.getMessage() == null) {
			meaningfulThrowable = t.getCause();
		}
		else {
			meaningfulThrowable = t;
		}

		if (meaningfulThrowable != t) {
			meaningfulThrowable = getMeaningfulThrowable(meaningfulThrowable);
		}

		return meaningfulThrowable;
	}

	/**
	 * @return The actual cause of an error by unwrapping exceptions as far as possible, i.e. NSForwardException.originalThrowable(),
	 * InvocationTargetException.getTargetException() or Exception.getCause() are regarded as actual causes.
	 */
	public static Throwable originalThrowable(Throwable t) {

		if (t instanceof InvocationTargetException it) {
			return originalThrowable(it.getTargetException());
		}

		if (t instanceof NSForwardException) {
			return originalThrowable(t.getCause());
		}

		if (t instanceof SQLException) {
			SQLException ex = (SQLException) t;
			if (ex.getNextException() != null) {
				return originalThrowable(ex.getNextException());
			}
		}

		if (t instanceof Exception) {
			Exception ex = (Exception) t;
			if (ex.getCause() != null) {
				return originalThrowable(ex.getCause());
			}
		}

		return t;
	}

	/**
	 * @return The current call stack, from the caller of this method down: a line separator, then a line per frame
	 *         ({@code \tat ...}), each ending with a line separator
	 */
	public static String stackTrace() {
		final String separator = System.lineSeparator();

		return StackWalker.getInstance().walk( frames -> frames
				.skip( 1 ) // this method
				.map( frame -> "\tat " + frame.toStackTraceElement() + separator )
				.collect( Collectors.joining( "", separator, "" ) ) );
	}
}