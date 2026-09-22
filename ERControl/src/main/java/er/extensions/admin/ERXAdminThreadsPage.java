package er.extensions.admin;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXComponent;

/**
 * A thread dump of this JVM, taken when the page renders.
 *
 * ERXMonitorServer serves the same information from a separate HTTP server, which is what one reaches for when this
 * server no longer answers. This page is for when it still does.
 */
public class ERXAdminThreadsPage extends ERXComponent {

	public record StateCount( String state, long count ) {}

	public ThreadInfo currentThread;
	public StateCount currentStateCount;

	private List<ThreadInfo> _threads;

	public ERXAdminThreadsPage( WOContext context ) {
		super( context );
	}

	@Override
	public void awake() {
		super.awake();
		_threads = null;
	}

	public List<ThreadInfo> threads() {
		if( _threads == null ) {
			final ThreadMXBean bean = ManagementFactory.getThreadMXBean();
			_threads = new ArrayList<>( Arrays.asList( bean.dumpAllThreads( bean.isObjectMonitorUsageSupported(), bean.isSynchronizerUsageSupported() ) ) );
			_threads.sort( Comparator.comparing( ( ThreadInfo t ) -> t.getThreadState().ordinal() ).thenComparing( ThreadInfo::getThreadName ) );
		}

		return _threads;
	}

	public List<StateCount> stateCounts() {
		final Map<String, Long> counts = new TreeMap<>();

		for( final ThreadInfo thread : threads() ) {
			counts.merge( thread.getThreadState().name(), 1L, Long::sum );
		}

		return counts.entrySet().stream().map( e -> new StateCount( e.getKey(), e.getValue() ) ).toList();
	}

	public int threadCount() {
		return threads().size();
	}

	public boolean hasDeadlock() {
		return ManagementFactory.getThreadMXBean().findDeadlockedThreads() != null;
	}

	public String currentStateClass() {
		return switch( currentThread.getThreadState() ) {
			case RUNNABLE -> "badge bg-success-lt";
			case BLOCKED -> "badge bg-danger-lt";
			case WAITING, TIMED_WAITING -> "badge";
			default -> "badge bg-warning-lt";
		};
	}

	public String currentLockDescription() {
		if( currentThread.getLockName() == null ) {
			return null;
		}

		return currentThread.getLockName() + ( currentThread.getLockOwnerName() == null ? "" : ", held by " + currentThread.getLockOwnerName() );
	}

	public String currentStackTrace() {
		final StringBuilder b = new StringBuilder();

		for( final StackTraceElement element : currentThread.getStackTrace() ) {
			b.append( "at " ).append( element ).append( '\n' );
		}

		return b.toString();
	}

	public boolean currentHasStackTrace() {
		return currentThread.getStackTrace().length > 0;
	}

	public WOActionResults refresh() {
		_threads = null;
		return null;
	}
}
