package er.extensions.admin;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSTimestamp;

import er.extensions.components.ERXComponent;
import er.extensions.foundation.ERXProperties;
import er.extensions.statistics.ERXStats;

/**
 * What the statistics store has counted since the instance started: transactions, sessions, memory, and the pages
 * and direct actions served. The same numbers WOStatsPage shows, read straight off the store's dictionaries.
 */
public class ERXAdminStatisticsPage extends ERXComponent {

	/**
	 * A page or direct action the store has counted, with its response times in seconds
	 */
	public record Served( String name, long count, double average, double min, double max, int percentOfBusiest ) {}

	public record Row( String label, String value ) {}

	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern( "yyyy-MM-dd HH:mm:ss" ).withZone( ZoneId.systemDefault() );

	public Served currentServed;
	public Row currentRow;

	private NSDictionary<String, Object> _statistics;

	public ERXAdminStatisticsPage( WOContext context ) {
		super( context );
	}

	@Override
	public void awake() {
		super.awake();
		_statistics = null;
	}

	@SuppressWarnings("unchecked")
	private NSDictionary<String, Object> statistics() {
		if( _statistics == null ) {
			_statistics = WOApplication.application().statistics();
		}

		return _statistics;
	}

	@SuppressWarnings("unchecked")
	private NSDictionary<String, Object> section( final String name ) {
		final Object value = statistics().objectForKey( name );
		return value instanceof NSDictionary ? (NSDictionary<String, Object>)value : NSDictionary.emptyDictionary();
	}

	private static long longValue( final NSDictionary<String, Object> d, final String key ) {
		return d.objectForKey( key ) instanceof Number n ? n.longValue() : 0;
	}

	private static double doubleValue( final NSDictionary<String, Object> d, final String key ) {
		return d.objectForKey( key ) instanceof Number n ? n.doubleValue() : 0;
	}

	// --- Headline ---

	public long transactions() {
		return longValue( section( "Transactions" ), "Transactions" );
	}

	public String averageTransactionTime() {
		return seconds( doubleValue( section( "Transactions" ), "Avg. Transaction Time" ) );
	}

	public String movingAverageTransactionTime() {
		return seconds( doubleValue( section( "Transactions" ), "Moving Avg. Transaction Time" ) );
	}

	public String transactionRate() {
		return NumberFormat.getInstance().format( doubleValue( section( "Transactions" ), "Transaction Rate" ) );
	}

	public long movingAverageSampleSize() {
		return longValue( section( "Transactions" ), "Sample Size For Moving Avg." );
	}

	public String startedAt() {
		return statistics().objectForKey( "StartedAt" ) instanceof NSTimestamp t ? TIMESTAMP.format( t.toInstant() ) : "";
	}

	public String runningTime() {
		if( !( statistics().objectForKey( "StartedAt" ) instanceof NSTimestamp t ) ) {
			return "";
		}

		final Duration d = Duration.ofMillis( System.currentTimeMillis() - t.getTime() );
		final long days = d.toDays();
		return ( days > 0 ? days + "d " : "" ) + d.toHoursPart() + "h " + d.toMinutesPart() + "m";
	}

	public long activeSessions() {
		return longValue( section( "Sessions" ), "Current Active Sessions" );
	}

	public long peakActiveSessions() {
		return longValue( section( "Sessions" ), "Peak Active Sessions" );
	}

	public String memoryUsed() {
		final NSDictionary<String, Object> m = section( "Memory" );
		final long total = longValue( m, "Total Memory" );
		final long free = longValue( m, "Free Memory" );
		return megabytes( total - free ) + " of " + megabytes( total ) + " MB";
	}

	public int memoryPercent() {
		final NSDictionary<String, Object> m = section( "Memory" );
		final long total = longValue( m, "Total Memory" );
		return total == 0 ? 0 : (int)( ( total - longValue( m, "Free Memory" ) ) * 100 / total );
	}

	// --- Tables ---

	public List<Row> transactionRows() {
		final NSDictionary<String, Object> t = section( "Transactions" );
		return List.of(
				new Row( "Component action transactions", count( longValue( t, "Component Action Transactions" ) ) + ", " + seconds( doubleValue( t, "Component Action  Avg. Transaction Time" ) ) + " average" ),
				new Row( "Direct action transactions", count( longValue( t, "Direct Action Transactions" ) ) + ", " + seconds( doubleValue( t, "Direct Action Avg. Transaction Time" ) ) + " average" ),
				new Row( "Average idle time", seconds( doubleValue( t, "Avg. Idle Time" ) ) ),
				new Row( "Moving average idle time", seconds( doubleValue( t, "Moving Avg. Idle Time" ) ) + " over the last " + movingAverageSampleSize() + " transactions" ) );
	}

	public List<Row> sessionRows() {
		final NSDictionary<String, Object> s = section( "Sessions" );
		final List<Row> rows = new ArrayList<>();
		rows.add( new Row( "Total sessions created", count( longValue( s, "Total Sessions Created" ) ) ) );
		rows.add( new Row( "Peak active sessions", count( peakActiveSessions() ) + ( s.objectForKey( "Peak Active Sessions Date" ) instanceof NSTimestamp t ? ", at " + TIMESTAMP.format( t.toInstant() ) : "" ) ) );
		rows.add( new Row( "Session rate", NumberFormat.getInstance().format( doubleValue( s, "Session Rate" ) ) + " per minute" ) );
		rows.add( new Row( "Average session life", seconds( doubleValue( s, "Avg. Session Life" ) ) ) );
		rows.add( new Row( "Average transactions per session", NumberFormat.getInstance().format( doubleValue( s, "Avg. Transactions Per Session" ) ) ) );
		rows.add( new Row( "Moving averages", seconds( doubleValue( s, "Moving Avg. Session Life" ) ) + " life, " + NumberFormat.getInstance().format( doubleValue( s, "Moving Avg. Transactions Per Session" ) ) + " transactions, over the last " + longValue( s, "Sample Size For Moving Avg." ) + " sessions" ) );
		return rows;
	}

	public List<Served> pages() {
		return served( section( "Pages" ) );
	}

	public List<Served> directActions() {
		return served( section( "DirectActions" ) );
	}

	@SuppressWarnings("unchecked")
	private static List<Served> served( final NSDictionary<String, Object> byName ) {
		final List<Served> result = new ArrayList<>();
		long busiest = 0;

		for( final Map.Entry<String, Object> entry : byName.entrySet() ) {
			if( entry.getValue() instanceof NSDictionary<?, ?> d ) {
				final NSDictionary<String, Object> stats = (NSDictionary<String, Object>)d;
				final long count = longValue( stats, "Served" );
				busiest = Math.max( busiest, count );
				result.add( new Served( entry.getKey(), count, doubleValue( stats, "Avg Resp. Time" ), doubleValue( stats, "Min Resp. Time" ), doubleValue( stats, "Max Resp. Time" ), 0 ) );
			}
		}

		result.sort( Comparator.comparingLong( Served::count ).reversed().thenComparing( Served::name ) );

		final long max = busiest;
		return result.stream().map( s -> new Served( s.name(), s.count(), s.average(), s.min(), s.max(), max == 0 ? 0 : (int)( s.count() * 100 / max ) ) ).toList();
	}

	public boolean hasPages() {
		return !pages().isEmpty();
	}

	public boolean hasDirectActions() {
		return !directActions().isEmpty();
	}

	public String currentServedAverage() {
		return seconds( currentServed.average() );
	}

	public String currentServedRange() {
		return seconds( currentServed.min() ) + " to " + seconds( currentServed.max() );
	}

	public String currentServedBarStyle() {
		return "width: " + currentServed.percentOfBusiest() + "%";
	}

	public String logFile() {
		return statistics().objectForKey( "LogFile" ) instanceof String s && !s.isEmpty() ? s : null;
	}

	// --- ERXStats ---

	public boolean isCollectingERXStats() {
		return ERXProperties.booleanForKey( "er.extensions.erxStats.enabled" );
	}

	public boolean hasERXStats() {
		return ERXStats.aggregateLogEntries().count() > 0;
	}

	public WOActionResults resetERXStats() {
		ERXStats.reset();
		return null;
	}

	public WOActionResults refresh() {
		_statistics = null;
		return null;
	}

	// --- Formatting ---

	private static String seconds( final double seconds ) {
		return seconds >= 1 ? String.format( "%.2f s", seconds ) : String.format( "%.0f ms", seconds * 1000 );
	}

	private static String count( final long n ) {
		return NumberFormat.getInstance().format( n );
	}

	private static long megabytes( final long bytes ) {
		return bytes / ( 1024 * 1024 );
	}
}
