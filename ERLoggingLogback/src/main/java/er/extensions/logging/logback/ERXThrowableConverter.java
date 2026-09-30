package er.extensions.logging.logback;

import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.CoreConstants;
import er.extensions.logging.ERXStackTraces;

/**
 * Writes a logged throwable's stack trace through {@link ERXStackTraces}, as the other logging backends do. The console
 * output the backend sets up uses it for {@code %ex}, {@code %exception} and {@code %throwable}; an application's own
 * logback.xml opts in with
 * {@code <conversionRule conversionWord="ex" class="er.extensions.logging.logback.ERXThrowableConverter"/>}.
 */
public class ERXThrowableConverter extends ThrowableHandlingConverter {

	@Override
	public String convert( final ILoggingEvent event ) {
		final IThrowableProxy proxy = event.getThrowableProxy();

		if( proxy == null ) {
			return CoreConstants.EMPTY_STRING;
		}

		// An event whose throwable isn't at hand (one read back from elsewhere) is written as logback writes it
		if( !(proxy instanceof ThrowableProxy throwableProxy) ) {
			return ThrowableProxyUtil.asString( proxy ) + CoreConstants.LINE_SEPARATOR;
		}

		return ERXStackTraces.format( throwableProxy.getThrowable() );
	}
}
