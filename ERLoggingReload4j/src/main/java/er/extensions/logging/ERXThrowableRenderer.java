package er.extensions.logging;

import org.apache.log4j.spi.ThrowableRenderer;

/**
 * Renders logged throwables through {@link ERXStackTraces}, for every appender. Installed on log4j's repository by
 * {@link ERXReload4jLoggingBackend}.
 */
public class ERXThrowableRenderer implements ThrowableRenderer {

	@Override
	public String[] doRender( final Throwable throwable ) {
		return ERXStackTraces.format( throwable ).split( "\\R" );
	}
}
