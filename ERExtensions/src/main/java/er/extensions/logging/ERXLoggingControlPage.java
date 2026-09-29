package er.extensions.logging;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
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

	/**
	 * The levels offered for each logger, most severe first
	 */
	private static final List<String> LEVEL_BUTTONS = List.of( "OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE" );

	/**
	 * A logger as the page lists it: as the backend has it, and its configuration, null if no layer sets its level
	 */
	public record Entry( String name, String level, String effectiveLevel, Row configured ) {

		public boolean isConfigured() {
			return configured != null;
		}

		public boolean isSetOnInstance() {
			return configured != null && configured.isSetOnInstance();
		}
	}

	public Entry currentEntry;
	public LoggerLevel currentLogger;
	public String currentLevel;
	public String currentKey;

	/**
	 * True to list every logger the backend knows, false for those with a level set in some layer
	 */
	public boolean showAll;

	/**
	 * Only loggers whose name contains this are listed
	 */
	public String filter;

	public String newLogger;
	public String newLevel = "DEBUG";
	public String notice;

	private List<Row> _rows;

	public ERXLoggingControlPage( WOContext context ) {
		super( context );
	}

	/**
	 * Reads the configuration afresh for each request, so the loggers listed are those the page was rendered with
	 */
	@Override
	public void awake() {
		super.awake();
		_rows = null;
	}

	/**
	 * Reads the configuration afresh once an action may have changed it
	 */
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

	/**
	 * @return Every logger, the backend's and those configured, the root logger first, then by name
	 */
	private List<Entry> allEntries() {
		final Map<String, Row> configured = new HashMap<>();
		rows().forEach( row -> configured.put( row.logger(), row ) );

		final List<Entry> entries = new ArrayList<>();
		final Set<String> listed = new HashSet<>();

		for( final LoggerLevel logger : loggers() ) {
			entries.add( new Entry( logger.name(), logger.level(), logger.effectiveLevel(), configured.get( logger.name() ) ) );
			listed.add( logger.name() );
		}

		// A logger configured but not yet created by the backend
		for( final Row row : rows() ) {
			if( !listed.contains( row.logger() ) ) {
				entries.add( new Entry( row.logger(), row.level(), row.level(), row ) );
			}
		}

		entries.sort( Comparator.comparing( ( Entry entry ) -> !entry.name().equals( ERXLoggingConfiguration.ROOT ) ).thenComparing( Entry::name ) );
		return entries;
	}

	/**
	 * @return The loggers listed: all of them or the configured ones, narrowed by the filter
	 */
	public List<Entry> entries() {
		return allEntries()
				.stream()
				.filter( entry -> showAll || entry.isConfigured() )
				.filter( entry -> filter == null || filter.isBlank() || entry.name().contains( filter.trim() ) )
				.toList();
	}

	public long configuredCount() {
		return rows().size();
	}

	public boolean hasEntries() {
		return !entries().isEmpty();
	}

	public List<String> levelButtons() {
		return LEVEL_BUTTONS;
	}

	/**
	 * @return The class of a level's button: its own level filled, an inherited effective level shaded
	 */
	public String currentLevelClass() {
		if( currentLevel.equals( currentEntry.level() ) ) {
			return "btn btn-xs btn-success";
		}

		if( currentEntry.level() == null && currentLevel.equals( currentEntry.effectiveLevel() ) ) {
			return "btn btn-xs btn-secondary";
		}

		return "btn btn-xs";
	}

	public String currentLevelTitle() {
		if( currentEntry.isSetOnInstance() && currentLevel.equals( currentEntry.configured().setBy().level() ) ) {
			return "Set on this instance: click to unset";
		}

		if( currentEntry.level() == null && currentLevel.equals( currentEntry.effectiveLevel() ) ) {
			return "Inherited";
		}

		return "Set on this instance";
	}

	public String showConfiguredClass() {
		return showAll ? "btn btn-sm" : "btn btn-sm btn-primary";
	}

	public String showAllClass() {
		return showAll ? "btn btn-sm btn-primary" : "btn btn-sm";
	}

	public WOActionResults listConfigured() {
		showAll = false;
		return null;
	}

	public WOActionResults listAll() {
		showAll = true;
		return null;
	}

	public WOActionResults applyFilter() {
		return null;
	}

	/**
	 * @return The package of the application's class, the likely root of its own loggers, null if it has none
	 */
	public String applicationPackage() {
		final String packageName = WOApplication.application().getClass().getPackageName();
		return packageName.isEmpty() ? null : packageName;
	}

	/**
	 * Lists every logger in the application's own package
	 */
	public WOActionResults showApplicationLoggers() {
		showAll = true;
		filter = applicationPackage();
		return null;
	}

	/**
	 * Sets the clicked level on this instance, or unsets it if it's the level already set on this instance
	 */
	public WOActionResults selectLevel() {
		final String key = ERXLoggingConfiguration.LEVEL_PREFIX + currentEntry.name();

		if( currentEntry.isSetOnInstance() && currentLevel.equals( currentEntry.configured().setBy().level() ) ) {
			ERXConfigurationManager.unsetProperty( key );
			notice = "%s unset: its level comes from the other layers again.".formatted( currentEntry.name() );
		}
		else {
			ERXConfigurationManager.setProperty( key, currentLevel );
			notice = "%s set to %s on this instance.".formatted( currentEntry.name(), currentLevel );
		}

		return null;
	}

	/**
	 * @return true if any level is set on this instance
	 */
	public boolean hasLevelsSetOnInstance() {
		return rows().stream().anyMatch( Row::isSetOnInstance );
	}

	/**
	 * Unsets every level set on this instance
	 */
	public WOActionResults unsetAll() {
		final List<Row> setOnInstance = rows().stream().filter( Row::isSetOnInstance ).toList();
		setOnInstance.forEach( row -> ERXConfigurationManager.unsetProperty( row.setBy().setting() ) );
		notice = "%d level(s) set on this instance unset.".formatted( setOnInstance.size() );
		return null;
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
		ERXConfigurationManager.unsetProperty( currentEntry.configured().setBy().setting() );
		notice = "%s unset: its level comes from the other layers again.".formatted( currentEntry.name() );
		return null;
	}
}
