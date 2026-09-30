package er.extensions.logging;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableRendererSupport;
import org.junit.jupiter.api.Test;

import com.webobjects.foundation.NSForwardException;

public class ERXThrowableRendererTest {

	@Test
	public void everyAppenderWritesTracesThroughERXStackTraces() {
		new ERXReload4jLoggingBackend().configure();
		assertInstanceOf( ERXThrowableRenderer.class, ((ThrowableRendererSupport)LogManager.getLoggerRepository()).getThrowableRenderer() );

		final Logger logger = Logger.getLogger( "test.trace" );
		final LoggingEvent event = new LoggingEvent( Logger.class.getName(), logger, Level.WARN, "Failed", new NSForwardException( new IllegalStateException( "EXPECTED-IN-TEST" ) ) );

		assertTrue( event.getThrowableStrRep()[0].equals( "java.lang.IllegalStateException: EXPECTED-IN-TEST" ), event.getThrowableStrRep()[0] );
	}
}
