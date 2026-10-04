package er.extensions.routing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Class file times telling whether declarations run again
 */
public class ClassChangesTest {

	@TempDir
	Path folder;

	private void write( final String name, final long millis ) throws IOException {
		final Path file = folder.resolve( name );
		Files.writeString( file, "" );
		Files.setLastModifiedTime( file, FileTime.fromMillis( millis ) );
	}

	@Test
	public void aNewerClassFileIsAChange() throws IOException {
		final AtomicLong now = new AtomicLong( 100_000 );
		final ClassChanges changes = new ClassChanges( now::get );
		write( "Routes.class", 50_000 );
		write( "notes.txt", 50_000 );
		changes.watch( folder );
		changes.built();

		assertFalse( changes.changed() );

		// Read at most once a second
		write( "Routes.class", 101_000 );
		assertFalse( changes.changed() );
		now.addAndGet( ClassChanges.CHECK_INTERVAL_MILLIS );
		assertTrue( changes.changed() );

		// Only class files count (built once the swap had time to land)
		now.addAndGet( ClassChanges.SWAP_DELAY_MILLIS );
		changes.built();
		write( "notes.txt", 200_000 );
		now.addAndGet( 10_000 );
		assertFalse( changes.changed() );
	}

	@Test
	public void aDeclarationRightAfterAChangeRunsOnceMoreAfterTheSwap() throws IOException {
		final AtomicLong now = new AtomicLong( 100_000 );
		final ClassChanges changes = new ClassChanges( now::get );
		write( "Routes.class", 99_500 );
		changes.watch( folder );

		// Declared half a second after the file was written: the new code may not have been there yet
		changes.built();
		assertFalse( changes.changed() );
		now.addAndGet( ClassChanges.SWAP_DELAY_MILLIS );
		assertTrue( changes.changed() );

		changes.built();
		now.addAndGet( 10_000 );
		assertFalse( changes.changed() );
	}
}
