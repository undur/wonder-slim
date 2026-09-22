package er.extensions.admin;

import java.util.ArrayList;
import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.eocontrol.EOAggregateEvent;
import com.webobjects.eocontrol.EOEvent;
import com.webobjects.eocontrol.EOEventCenter;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSMutableArray;

import er.extensions.components.ERXComponent;

/**
 * WOEvent instrumentation: which event classes are recorded, and what has been recorded. The same data
 * WOEventSetupPage and WOEventDisplayPage show; the grouping and aggregation of the tree follows the latter's
 * display modes.
 */
public class ERXAdminEventsPage extends ERXComponent {

	/**
	 * An event class that can be recorded
	 */
	public record EventClass( Class<?> type ) {

		public String name() {
			return type.getName();
		}
	}

	/**
	 * A line of the event tree, flattened for display
	 */
	public record Line( int depth, String title, String comment, long duration, int calls, int percentOfTopmost, boolean hasChildren ) {}

	public record Mode( int value, String label ) {}

	public static final List<Mode> MODES = List.of(
			new Mode( 0, "All root events" ),
			new Mode( 1, "Aggregated, hierarchical" ),
			new Mode( 2, "By page, then by component" ),
			new Mode( 3, "By page" ),
			new Mode( 4, "By page, associations only" ) );

	private static final int MAX_DEPTH = 12;
	private static final int MAX_LINES = 2000;

	public EventClass currentClass;
	public String currentKind;
	public Line currentLine;
	public Mode currentMode;

	public int displayMode = 1;

	private List<Line> _lines;
	private long _topmostDuration;

	public ERXAdminEventsPage( WOContext context ) {
		super( context );
	}

	@Override
	public void awake() {
		super.awake();
		_lines = null;
	}

	// --- Setup ---

	@SuppressWarnings("unchecked")
	public List<EventClass> eventClasses() {
		final List<EventClass> result = new ArrayList<>();

		for( final Class<?> c : (NSArray<Class<?>>)EOEventCenter.registeredEventClasses() ) {
			result.add( new EventClass( c ) );
		}

		result.sort( ( a, b ) -> a.name().compareTo( b.name() ) );
		return result;
	}

	public boolean currentClassIsRecorded() {
		return EOEventCenter.recordsEventsForClass( currentClass.type() );
	}

	public void setCurrentClassIsRecorded( final boolean recorded ) {
		EOEventCenter.setRecordsEvents( recorded, currentClass.type() );
	}

	public boolean isRecordingAnything() {
		return eventClasses().stream().anyMatch( c -> EOEventCenter.recordsEventsForClass( c.type() ) );
	}

	public WOActionResults applySetup() {
		return null;
	}

	public WOActionResults recordAll() {
		eventClasses().forEach( c -> EOEventCenter.setRecordsEvents( true, c.type() ) );
		return null;
	}

	public WOActionResults recordNone() {
		eventClasses().forEach( c -> EOEventCenter.setRecordsEvents( false, c.type() ) );
		return null;
	}

	// --- The recorded events ---

	public List<Mode> modes() {
		return MODES;
	}

	public String currentModeClass() {
		return currentMode.value() == displayMode ? "btn btn-sm btn-primary" : "btn btn-sm";
	}

	public WOActionResults selectCurrentMode() {
		displayMode = currentMode.value();
		_lines = null;
		return null;
	}

	public int eventCount() {
		return EOEventCenter.allEventsForAllCenters().count();
	}

	public boolean hasEvents() {
		return eventCount() > 0;
	}

	public WOActionResults resetEvents() {
		EOEventCenter.resetLoggingForAllCenters();
		_lines = null;
		return null;
	}

	public WOActionResults refresh() {
		_lines = null;
		return null;
	}

	public List<Line> lines() {
		if( _lines == null ) {
			_lines = new ArrayList<>();
			final NSArray<EOEvent> roots = prepared( displayMode <= 1 ? EOEventCenter.rootEventsForAllCenters() : EOEventCenter.allEventsForAllCenters(), 0 );
			_topmostDuration = roots.isEmpty() ? 0 : duration( roots.objectAtIndex( 0 ) );

			for( final EOEvent root : roots ) {
				add( root, 0 );
			}
		}

		return _lines;
	}

	public boolean linesTruncated() {
		return lines().size() >= MAX_LINES;
	}

	private void add( final EOEvent event, final int depth ) {

		if( _lines.size() >= MAX_LINES ) {
			return;
		}

		final NSArray<EOEvent> children = depth < MAX_DEPTH ? children( event, depth + 1 ) : NSArray.emptyArray();
		final long duration = duration( event );
		final int calls = event instanceof EOAggregateEvent a ? a.events().count() : 1;
		_lines.add( new Line( depth, event.title(), event.comment(), duration, calls, _topmostDuration == 0 ? 0 : (int)Math.min( 100, duration * 100 / _topmostDuration ), !children.isEmpty() ) );

		for( final EOEvent child : children ) {
			add( child, depth + 1 );
		}
	}

	@SuppressWarnings("unchecked")
	private NSArray<EOEvent> children( final EOEvent event, final int level ) {
		final NSArray<EOEvent> subevents = event.subevents();
		return subevents == null || subevents.isEmpty() ? NSArray.emptyArray() : prepared( subevents, level );
	}

	/**
	 * Groups, aggregates, filters and sorts events for a level of the tree, as WOEventDisplayPage does for its display modes.
	 */
	@SuppressWarnings("unchecked")
	private NSArray<EOEvent> prepared( NSArray<EOEvent> events, final int level ) {
		final int groupTag = groupTag( level );

		if( groupTag >= 0 ) {
			events = EOEvent.groupEvents( events, groupTag );
		}

		final int aggregateTag = aggregateTag( level );

		if( aggregateTag >= 0 ) {
			events = EOEvent.aggregateEvents( events, aggregateTag );
		}

		final NSMutableArray<EOEvent> result = new NSMutableArray<>();

		for( final EOEvent event : events ) {
			// Associations only: at the root, keep events that have something beneath them
			if( displayMode == 4 && level == 0 && ( event.subevents() == null || event.subevents().isEmpty() ) ) {
				continue;
			}

			result.add( event );
		}

		result.sort( ( a, b ) -> Long.compare( duration( b ), duration( a ) ) );
		return result;
	}

	private int groupTag( final int level ) {
		return switch( displayMode ) {
			case 2 -> level == 0 ? 2 : level == 1 ? 1 : -1;
			case 3, 4 -> level == 0 ? 2 : -1;
			default -> -1;
		};
	}

	private int aggregateTag( final int level ) {
		return switch( displayMode ) {
			case 1 -> 0;
			case 2 -> level <= 1 ? -1 : 0;
			case 3 -> level == 0 ? -1 : 0;
			case 4 -> level == 0 ? -1 : 3;
			default -> -1;
		};
	}

	@SuppressWarnings("unchecked")
	private long duration( final EOEvent event ) {

		if( displayMode != 4 ) {
			return event.duration();
		}

		// Associations only: an event's time is the time of what is beneath it, when anything is
		final NSArray<EOEvent> subevents = event.subevents();

		if( subevents == null || subevents.isEmpty() ) {
			return event.duration();
		}

		long sum = 0;

		for( final EOEvent e : subevents ) {
			sum += e.duration();
		}

		return sum;
	}

	// --- Line rendering ---

	public String currentLineIndentStyle() {
		return "padding-left: " + ( 1.25 + currentLine.depth() * 1.25 ) + "rem";
	}

	public String currentLineBarStyle() {
		return "width: " + currentLine.percentOfTopmost() + "%";
	}

	public String currentLineCalls() {
		return currentLine.calls() + "x";
	}

	public String currentLineTitleClass() {
		return currentLine.hasChildren() ? "font-weight-bold" : "";
	}
}
