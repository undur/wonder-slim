package er.extensions.foundation;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;

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
	 * @return A string representation of the current stacktrace.
	 */
	public static String stackTrace() {

		String result = stackTrace(new Throwable());
	
		final String separator = System.getProperties().getProperty("line.separator");
	
		// Chop off the 1st line, "java.lang.Throwable"
		int offset = result.indexOf(separator);
		result = result.substring(offset + 1);
	
		// Chop off the lines at the start that refer to this class
		offset = result.indexOf(separator);

		while (result.substring(0, offset).indexOf("ERXExceptionUtilities.java") >= 0) {
			result = result.substring(offset + 1);
			offset = result.indexOf(separator);
		}

		return separator + result;
	}
	
	/**
	 * @return The throwable's stacktrace as a string
	 */
	private static String stackTrace(Throwable t) {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
		final PrintStream printStream = new PrintStream(baos);
		t.printStackTrace(printStream);
		return baos.toString();
	}
}