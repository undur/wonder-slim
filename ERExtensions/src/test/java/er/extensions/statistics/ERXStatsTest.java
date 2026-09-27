package er.extensions.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import er.extensions.statistics.ERXStats.LogEntry;

public class ERXStatsTest {

	private static LogEntry entry( final String key, final long... times ) {
		final LogEntry entry = new LogEntry( key );

		for( final long time : times ) {
			entry.add( time );
		}

		return entry;
	}

	private static List<String> keysInOrder( final String operation ) {
		final List<LogEntry> entries = new ArrayList<>( List.of( entry( "b", 5, 5, 5 ), entry( "C", 100 ), entry( "a", 1, 40 ) ) );
		entries.sort( ERXStats.orderForOperation( operation ) );
		return entries.stream().map( LogEntry::key ).toList();
	}

	@Test
	public void ordersByOperation() {
		assertEquals( List.of( "b", "a", "C" ), keysInOrder( "sum" ) );
		assertEquals( List.of( "C", "a", "b" ), keysInOrder( "count" ) );
		assertEquals( List.of( "a", "b", "C" ), keysInOrder( "min" ) );
		assertEquals( List.of( "b", "a", "C" ), keysInOrder( "max" ) );
		assertEquals( List.of( "b", "a", "C" ), keysInOrder( "avg" ) );
		assertEquals( List.of( "a", "b", "C" ), keysInOrder( "key" ) );
	}

	@Test
	public void unknownOperationThrows() {
		assertThrows( IllegalArgumentException.class, () -> ERXStats.orderForOperation( "median" ) );
		assertThrows( IllegalArgumentException.class, () -> ERXStats.orderForOperation( null ) );
	}
}
