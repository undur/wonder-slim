package er.routing;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * EXPERIMENTAL (route-links branch). Whether the classes route declarations depend on changed since they last ran: the
 * class files in the folders of the declarations' holders and the routes' records (a compiled folder, as in
 * development). A class from a jar can't change while the application runs, so it isn't watched.
 */

class ClassChanges implements RouteDeclarations.Changes {

	/**
	 * How long after a class file is written its new code may take to reach the running application (the hot swap
	 * landing, in development), observed to be a second or two. A declaration run sooner than this after a change is run
	 * once more after it, in case it ran the old code.
	 */
	static final long SWAP_DELAY_MILLIS = 3000;

	/**
	 * How long the newest class file's time is kept before the folders are read again: a routed request in development
	 * reads them at most this often
	 */
	static final long CHECK_INTERVAL_MILLIS = 1000;

	private final Set<Path> _folders = ConcurrentHashMap.newKeySet();
	private final LongSupplier _clock;
	private volatile long _builtFor;
	private volatile boolean _settled = true;
	private volatile long _newest;
	private volatile long _checkedAt;

	/**
	 * True when the folders are read on the next check, whenever that is
	 */
	private volatile boolean _stale = true;

	ClassChanges() {
		this( System::currentTimeMillis );
	}

	/**
	 * @param clock The time now, in milliseconds
	 */
	ClassChanges( final LongSupplier clock ) {
		_clock = clock;
	}

	/**
	 * Watches the class files in a folder
	 */
	void watch( final Path folder ) {
		_folders.add( folder );
		_stale = true;
	}

	@Override
	public void watch( final Class<?> type ) {
		final String fileName = type.getName().substring( type.getName().lastIndexOf( '.' ) + 1 ) + ".class";
		final URL url = type.getResource( fileName );

		if( url != null && "file".equals( url.getProtocol() ) ) {
			try {
				watch( Path.of( url.toURI() ).getParent() );
			}
			catch( URISyntaxException e ) {
				// Not a path, so not watched
			}
		}
	}

	@Override
	public boolean changed() {
		final long newest = newest();
		return newest > _builtFor || (!_settled && _clock.getAsLong() - newest >= SWAP_DELAY_MILLIS);
	}

	@Override
	public void built() {
		_stale = true;
		_builtFor = newest();
		_settled = _clock.getAsLong() - _builtFor >= SWAP_DELAY_MILLIS;
	}

	/**
	 * @return The time the newest class file in the watched folders was written, read at most once a
	 *         {@link #CHECK_INTERVAL_MILLIS}
	 */
	private long newest() {
		final long now = _clock.getAsLong();

		if( _stale || now - _checkedAt >= CHECK_INTERVAL_MILLIS ) {
			_newest = readNewest();
			_checkedAt = now;
			_stale = false;
		}

		return _newest;
	}

	private long readNewest() {
		long newest = 0;

		for( final Path folder : _folders ) {
			try( Stream<Path> files = Files.list( folder ) ) {
				for( final Path file : (Iterable<Path>)files::iterator ) {
					if( file.getFileName().toString().endsWith( ".class" ) ) {
						newest = Math.max( newest, Files.getLastModifiedTime( file ).toMillis() );
					}
				}
			}
			catch( IOException e ) {
				// A folder being rewritten (a clean build): its files count once they're there
			}
		}

		return newest;
	}
}
