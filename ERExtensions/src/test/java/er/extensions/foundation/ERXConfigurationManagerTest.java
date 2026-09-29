package er.extensions.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The configuration is composed once per JVM, so the tests share one composition: an optional configuration file
 * named by an argument, which points to a machine properties directory holding a second file. Each test writes the
 * files it needs and reloads.
 */
public class ERXConfigurationManagerTest {

	private static Path _folder;
	private static Path _optionalFile;
	private static Path _machineFile;

	@BeforeAll
	public static void compose() throws IOException {
		_folder = Files.createTempDirectory( "ERXConfigurationManagerTest" );
		_optionalFile = _folder.resolve( "optional.properties" );
		_machineFile = _folder.resolve( "Properties" );

		System.setProperty( "er.test.launch", "fromLaunch" );

		writeOptional( "er.test.file=fromFile", "er.test.overridden=fromFile", "er.test.launch=fromFile" );

		if( ERXConfigurationManager.current() == null ) {
			ERXConfigurationManager.compose( new String[] { "-er.extensions.ERXProperties.OptionalConfigurationFiles", "(" + _optionalFile + ")", "-er.test.overridden", "fromArgument" } );
		}
		else {
			ERXConfigurationManager.reload();
		}
	}

	@AfterAll
	public static void removeFiles() throws IOException {
		Files.deleteIfExists( _optionalFile );
		Files.deleteIfExists( _machineFile );
		Files.deleteIfExists( _folder );
		ERXConfigurationManager.reload();
	}

	private static void writeOptional( final String... lines ) throws IOException {
		Files.writeString( _optionalFile, String.join( "\n", lines ) );
	}

	@Test
	public void aFileNamedByAnArgumentIsRead() throws IOException {
		writeOptional( "er.test.file=fromFile", "er.test.overridden=fromFile", "er.test.launch=fromFile" );
		ERXConfigurationManager.reload();

		assertEquals( "fromFile", System.getProperty( "er.test.file" ) );
		assertEquals( "Optional Configuration", ERXConfigurationManager.current().origin( "er.test.file" ).name() );
	}

	@Test
	public void anArgumentOverridesAFile() throws IOException {
		writeOptional( "er.test.overridden=fromFile" );
		ERXConfigurationManager.reload();

		assertEquals( "fromArgument", System.getProperty( "er.test.overridden" ) );
		assertEquals( "Arguments", ERXConfigurationManager.current().origin( "er.test.overridden" ).name() );
	}

	@Test
	public void aPropertyNoLongerSetGetsItsValueFromLaunchBack() throws IOException {
		writeOptional( "er.test.launch=fromFile", "er.test.file=fromFile" );
		ERXConfigurationManager.reload();
		assertEquals( "fromFile", System.getProperty( "er.test.launch" ) );
		assertEquals( "fromFile", System.getProperty( "er.test.file" ) );

		writeOptional( "er.test.other=x" );
		ERXConfigurationManager.reload();
		assertEquals( "fromLaunch", System.getProperty( "er.test.launch" ) );
		assertNull( System.getProperty( "er.test.file" ), "A property with no value at launch is removed" );
		assertNull( ERXConfigurationManager.current().origin( "er.test.launch" ), "A value from launch has no source" );
	}

	@Test
	public void aFileNamedByAnotherFileIsFoundInTheNextRound() throws IOException {
		Files.writeString( _machineFile, "er.test.machine=fromMachine" );
		writeOptional( "er.extensions.ERXProperties.machinePropertiesPath=" + _folder );
		ERXConfigurationManager.reload();

		assertEquals( "fromMachine", System.getProperty( "er.test.machine" ) );
		assertEquals( "Application-Machine Properties", ERXConfigurationManager.current().origin( "er.test.machine" ).name() );
		assertTrue( ERXConfigurationManager.current().watchedFiles().contains( _machineFile.toFile().getCanonicalFile() ) );

		Files.delete( _machineFile );
		writeOptional( "er.test.other=x" );
		ERXConfigurationManager.reload();
		assertNull( System.getProperty( "er.test.machine" ) );
	}

	@Test
	public void aFileLookedForWhereNoFileIsPresentIsRecordedAndWatchedButNotRead() throws IOException {
		writeOptional( "er.extensions.ERXProperties.machinePropertiesPath=" + _folder );
		ERXConfigurationManager.reload();

		final ERXConfigurationManager.Source machine = ERXConfigurationManager.current()
				.sources()
				.stream()
				.filter( source -> source.location().equals( _machineFile.toString() ) )
				.findFirst()
				.orElseThrow();

		assertTrue( !machine.found() );
		assertTrue( machine.properties().isEmpty() );
		assertTrue( ERXConfigurationManager.current().watchedFiles().contains( _machineFile.toFile() ), "Watched, so a file created there is picked up" );
	}

	@Test
	public void theWatcherReloadsWhenAFileChanges() throws IOException {
		writeOptional( "er.test.watched=before" );
		ERXConfigurationManager.reload();
		final ERXConfigurationManager.Watcher watcher = new ERXConfigurationManager.Watcher( null );

		assertTrue( !watcher.check(), "Nothing has changed yet" );

		writeOptional( "er.test.watched=after" );
		touch( _optionalFile );
		assertTrue( watcher.check() );
		assertEquals( "after", System.getProperty( "er.test.watched" ) );
		assertTrue( !watcher.check(), "Reloaded once" );
	}

	@Test
	public void theWatcherPicksUpAFileCreatedWhereNoneWasPresent() throws IOException {
		writeOptional( "er.extensions.ERXProperties.machinePropertiesPath=" + _folder );
		Files.deleteIfExists( _machineFile );
		ERXConfigurationManager.reload();
		final ERXConfigurationManager.Watcher watcher = new ERXConfigurationManager.Watcher( null );

		Files.writeString( _machineFile, "er.test.created=yes" );
		assertTrue( watcher.check() );
		assertEquals( "yes", System.getProperty( "er.test.created" ) );

		Files.delete( _machineFile );
		assertTrue( watcher.check() );
		assertNull( System.getProperty( "er.test.created" ) );
	}

	@Test
	public void theWatcherWatchesOnlyTheTouchFilesItsGiven() throws IOException {
		final Path touchFile = _folder.resolve( "touch" );
		Files.writeString( touchFile, "" );
		final ERXConfigurationManager.Watcher watcher = new ERXConfigurationManager.Watcher( List.of( touchFile.toFile() ) );

		writeOptional( "er.test.touched=before-touch" );
		touch( _optionalFile );
		assertTrue( !watcher.check(), "A configuration file changing isn't noticed" );

		touch( touchFile );
		assertTrue( watcher.check() );
		assertEquals( "before-touch", System.getProperty( "er.test.touched" ) );
		Files.delete( touchFile );
	}

	/**
	 * Moves the file's modification time on, since a file written twice within a second may keep its time
	 */
	private static void touch( final Path file ) {
		file.toFile().setLastModified( file.toFile().lastModified() + 2000 );
	}

	@Test
	public void aPropertySetOnTheInstanceOverridesEverySourceUntilUnset() throws IOException {
		writeOptional( "er.test.instance=fromFile", "er.test.overridden=fromFile" );
		ERXConfigurationManager.reload();

		ERXConfigurationManager.setProperty( "er.test.instance", "fromConsole" );
		ERXConfigurationManager.setProperty( "er.test.overridden", "fromConsole" );
		assertEquals( "fromConsole", System.getProperty( "er.test.instance" ) );
		assertEquals( "fromConsole", System.getProperty( "er.test.overridden" ), "Above the arguments too" );
		assertTrue( ERXConfigurationManager.current().origin( "er.test.instance" ).isInstanceSource() );

		ERXConfigurationManager.reload();
		assertEquals( "fromConsole", System.getProperty( "er.test.instance" ), "Survives a reload" );

		ERXConfigurationManager.unsetProperty( "er.test.instance" );
		ERXConfigurationManager.unsetProperty( "er.test.overridden" );
		assertEquals( "fromFile", System.getProperty( "er.test.instance" ) );
		assertEquals( "fromArgument", System.getProperty( "er.test.overridden" ) );
		assertEquals( "Optional Configuration", ERXConfigurationManager.current().origin( "er.test.instance" ).name() );
	}

	@Test
	public void unsettingAPropertyNoOtherSourceSetsRemovesIt() {
		ERXConfigurationManager.setProperty( "er.test.consoleOnly", "x" );
		assertEquals( "x", ERXProperties.stringForKey( "er.test.consoleOnly" ) );

		ERXConfigurationManager.unsetProperty( "er.test.consoleOnly" );
		assertNull( System.getProperty( "er.test.consoleOnly" ) );
		assertNull( ERXProperties.stringForKey( "er.test.consoleOnly" ) );
	}

	@Test
	public void aListenerHearsAChangeInItsProperty() throws IOException {
		writeOptional( "er.test.watchedValue=one" );
		ERXConfigurationManager.reload();

		final List<ERXConfigurationManager.PropertyChange> heard = new ArrayList<>();
		final ERXConfigurationManager.ChangeListener listener = ERXConfigurationManager.onChange( "er.test.watchedValue", heard::add );

		try {
			writeOptional( "er.test.watchedValue=two", "er.test.unrelated=x" );
			ERXConfigurationManager.reload();
			assertEquals( List.of( new ERXConfigurationManager.PropertyChange( "er.test.watchedValue", "one", "two" ) ), heard );

			ERXConfigurationManager.reload();
			assertEquals( 1, heard.size(), "A reload that changes nothing isn't a change" );

			ERXConfigurationManager.setProperty( "er.test.watchedValue", "three" );
			ERXConfigurationManager.unsetProperty( "er.test.watchedValue" );
			writeOptional( "er.test.unrelated=x" );
			ERXConfigurationManager.reload();

			assertEquals( List.of(
					new ERXConfigurationManager.PropertyChange( "er.test.watchedValue", "one", "two" ),
					new ERXConfigurationManager.PropertyChange( "er.test.watchedValue", "two", "three" ),
					new ERXConfigurationManager.PropertyChange( "er.test.watchedValue", "three", "two" ),
					new ERXConfigurationManager.PropertyChange( "er.test.watchedValue", "two", null ) ), heard );
		}
		finally {
			listener.remove();
		}
	}

	@Test
	public void aListenerCanWatchAFamilyOfPropertiesAndReadTheNewValues() {
		final List<String> heard = new ArrayList<>();
		final ERXConfigurationManager.ChangeListener listener = ERXConfigurationManager.onChange( key -> key.startsWith( "er.test.family." ), change -> heard.add( change.propertyName() + "=" + ERXProperties.stringForKey( change.propertyName() ) ) );

		try {
			ERXConfigurationManager.setProperty( "er.test.family.a", "1" );
			ERXConfigurationManager.setProperty( "er.test.other", "2" );
			assertEquals( List.of( "er.test.family.a=1" ), heard );
		}
		finally {
			listener.remove();
			ERXConfigurationManager.unsetProperty( "er.test.family.a" );
			ERXConfigurationManager.unsetProperty( "er.test.other" );
		}
	}

	@Test
	public void aRemovedListenerHearsNothingAndAFailingOneDoesntStopTheOthers() {
		final List<String> heard = new ArrayList<>();
		final ERXConfigurationManager.ChangeListener failing = ERXConfigurationManager.onChange( "er.test.failing", change -> { throw new IllegalStateException( "on purpose" ); } );
		final ERXConfigurationManager.ChangeListener removed = ERXConfigurationManager.onChange( "er.test.failing", change -> heard.add( "removed" ) );
		final ERXConfigurationManager.ChangeListener working = ERXConfigurationManager.onChange( "er.test.failing", change -> heard.add( "working" ) );
		removed.remove();

		try {
			ERXConfigurationManager.setProperty( "er.test.failing", "x" );
			assertEquals( List.of( "working" ), heard );
		}
		finally {
			failing.remove();
			working.remove();
			ERXConfigurationManager.unsetProperty( "er.test.failing" );
		}
	}

	@Test
	public void aChangeMasksSecretsWhenPrinted() {
		final String printed = new ERXConfigurationManager.PropertyChange( "db.password", "old-secret", "new-secret" ).toString();
		assertTrue( !printed.contains( "secret" ), printed );
	}

	@Test
	public void theReadsGoThroughTheCaches() throws IOException {
		writeOptional( "er.test.cached=before" );
		ERXConfigurationManager.reload();
		assertEquals( "before", ERXProperties.stringForKey( "er.test.cached" ) );

		writeOptional( "er.test.cached=after" );
		ERXConfigurationManager.reload();
		assertEquals( "after", ERXProperties.stringForKey( "er.test.cached" ) );
	}

	@Test
	public void aPreviewIsNotApplied() throws IOException {
		writeOptional( "er.test.file=fromFile" );
		ERXConfigurationManager.reload();
		final ERXConfigurationManager current = ERXConfigurationManager.current();

		writeOptional( "er.test.file=changed" );
		final ERXConfigurationManager preview = ERXConfigurationManager.previewForUser( "nobody-in-particular" );

		assertEquals( "changed", preview.properties().get( "er.test.file" ) );
		assertEquals( "fromFile", System.getProperty( "er.test.file" ) );
		assertSame( current, ERXConfigurationManager.current() );
	}
}
