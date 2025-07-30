package er.extensions.appserver;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSPropertyListSerialization;

/**
 * Keeps track of exceptions caught by ERXApplication.handleException()
 */

public class ERXExceptionManager {

	/**
	 * A class for keeping info about our thrown exception, along with some metadata 
	 */
	public record LoggedException( Throwable throwable, LocalDateTime dateTime, String id, NSDictionary extraInfo ) {
		
		public String extraInfoString() {
			return NSPropertyListSerialization.stringFromPropertyList(extraInfo);
		}
		
		public String stackTraceString() {
			final StringWriter sw = new StringWriter();
			throwable().printStackTrace(new PrintWriter(sw));
			return sw.toString();
		}
	}

	private final List<LoggedException> _loggedExceptions = Collections.synchronizedList(new ArrayList<>());

	/**
	 * @return All exceptions handled by ERXApplication.handleException()
	 */
	public List<LoggedException> loggedExceptions() {
		return _loggedExceptions;
	}

	/**
	 * @return an exception logged with the given data
	 */
	public LoggedException log( Throwable throwable, LocalDateTime dateTime, String id, NSDictionary extraInfo  ) {
		var l = new LoggedException(throwable, dateTime, id, extraInfo );
		_loggedExceptions.add(l);
		return l;
	}
	
	/**
	 * @return A list of the types of thrown exceptions
	 */
	public List<?> exceptionClasses() {
		final List<?> exceptionClasses = _loggedExceptions
				.stream()
				.map(LoggedException::throwable)
				.map(Throwable::getClass)
				.distinct()
				.sorted( Comparator.comparing(Class::getName))
				.toList();

		return exceptionClasses;
	}
}