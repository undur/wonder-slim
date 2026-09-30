package er.extensions.logging;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import er.extensions.foundation.ERXThreadStorage;

/**
 * ERXConsoleAppender is just like ConsoleAppender except that it will not log the same exception twice in a row,
 * preventing the annoying problem where you may log from multiple places in your code and produce multiple copies of
 * the same exception trace. Stack traces are written as with every appender, see {@link ERXStackTraces}.
 *
 * @author mschrag
 */
public class ERXConsoleAppender extends ConsoleAppender {

	private static final String LAST_THROWABLE_KEY = "er.extensions.logging.ERXConsoleAppender.lastThrowable";

	public ERXConsoleAppender() {
		super();
	}

	@SuppressWarnings("hiding")
	public ERXConsoleAppender( Layout layout ) {
		super( layout );
	}

	@SuppressWarnings("hiding")
	public ERXConsoleAppender( Layout layout, String target ) {
		super( layout, target );
	}

	@Override
	protected void subAppend( LoggingEvent event ) {
		qw.write( super.layout.format( event ) );

		if( super.layout.ignoresThrowable() ) {
			final ThrowableInformation throwableInfo = event.getThrowableInformation();

			if( throwableInfo != null ) {
				final Throwable throwable = throwableInfo.getThrowable();

				if( throwable != null && throwable != ERXThreadStorage.valueForKey( LAST_THROWABLE_KEY ) ) {
					final String[] lines = event.getThrowableStrRep();

					if( lines != null ) {
						for( final String line : lines ) {
							qw.write( line );
							qw.write( Layout.LINE_SEP );
						}
					}

					ERXThreadStorage.takeValueForKey( throwable, LAST_THROWABLE_KEY );
				}
			}
		}

		if( immediateFlush ) {
			qw.flush();
		}
	}
}
