package er.extensions.logging;

import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;

import er.extensions.components.ERXComponent;
import er.extensions.foundation.ERXConfigurationManager;
import er.extensions.logging.ERXLoggingBackend.LoggerLevel;
import er.extensions.logging.ERXLoggingOverview.Row;

/**
 * The control panel's Logging page: the backend, where each logger's level comes from, and levels set on the running
 * instance. Registered by ERExtensions' plugin, shown by ERControl.
 */
public class ERXLoggingControlPage extends ERXComponent {

	/**
	 * The levels a level can be set to
	 */
	public static final List<String> LEVELS = List.of( "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "OFF" );

	public Row currentRow;
	public LoggerLevel currentLogger;
	public String currentLevel;
	public String currentKey;

	public String newLogger;
	public String newLevel = "DEBUG";
	public String notice;

	private List<Row> _rows;

	public ERXLoggingControlPage( WOContext context ) {
		super( context );
	}

	@Override
	public void appendToResponse( final WOResponse response, final WOContext context ) {
		_rows = null;
		super.appendToResponse( response, context );
	}

	public ERXLoggingBackend backend() {
		return ERXLoggingSupport.backend();
	}

	public boolean hasBackend() {
		return backend() != null;
	}

	public String nativeConfiguration() {
		final String location = backend().nativeConfiguration();
		return location == null ? "none" : location;
	}

	public String pattern() {
		return ERXLoggingConfiguration.pattern( System.getProperties() );
	}

	/**
	 * @return The Project Wonder style log4j output settings the backend ignores, empty if it reads them
	 */
	public List<String> ignoredKeys() {
		return backend().readsLog4jConfiguration() ? List.of() : ERXLoggingConfiguration.legacyOutputKeys( System.getProperties() );
	}

	public boolean hasIgnoredKeys() {
		return !ignoredKeys().isEmpty();
	}

	public List<Row> rows() {
		if( _rows == null ) {
			_rows = ERXLoggingOverview.rows( backend() );
		}

		return _rows;
	}

	public List<LoggerLevel> loggers() {
		return backend().loggers();
	}

	public int loggerCount() {
		return loggers().size();
	}

	public List<String> levels() {
		return LEVELS;
	}

	/**
	 * Sets the level of the logger named on this instance
	 */
	public WOActionResults setLevel() {
		if( newLogger == null || newLogger.isBlank() ) {
			notice = "Name a logger, or root for the root logger.";
			return null;
		}

		final String logger = newLogger.trim();
		ERXConfigurationManager.setProperty( ERXLoggingConfiguration.LEVEL_PREFIX + logger, newLevel );
		notice = "%s set to %s on this instance.".formatted( logger, newLevel );
		newLogger = null;
		return null;
	}

	/**
	 * Unsets the level set on this instance for the current row's logger
	 */
	public WOActionResults unsetLevel() {
		ERXConfigurationManager.unsetProperty( currentRow.setBy().setting() );
		notice = "%s unset: its level comes from the other layers again.".formatted( currentRow.logger() );
		return null;
	}
}
