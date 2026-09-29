package er.extensions.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ERXEarlyOutputTest {

	private PrintStream _out;
	private PrintStream _err;

	private final ByteArrayOutputStream _console = new ByteArrayOutputStream();

	@BeforeEach
	public void console() {
		// Another test may have initialized ERXApplication, whose static initializer starts keeping
		ERXEarlyOutput.finish();

		_out = System.out;
		_err = System.err;
		System.setOut( new PrintStream( _console, true, StandardCharsets.UTF_8 ) );
		System.setErr( new PrintStream( _console, true, StandardCharsets.UTF_8 ) );
	}

	@AfterEach
	public void restore() {
		ERXEarlyOutput.finish();
		System.setOut( _out );
		System.setErr( _err );
	}

	@Test
	public void whatWasWrittenBeforeTheRedirectGoesAtTheStartOfTheFile() {
		ERXEarlyOutput.start();
		System.out.println( "early out" );
		System.err.println( "early err" );
		assertEquals( "early out\nearly err\n", _console.toString( StandardCharsets.UTF_8 ), "Still written to the console as before" );

		// WebObjects redirects to the WOOutputPath file
		final ByteArrayOutputStream file = new ByteArrayOutputStream();
		System.setOut( new PrintStream( file, true, StandardCharsets.UTF_8 ) );
		System.setErr( System.out );
		System.out.println( "written by WebObjects after its redirect" );

		ERXEarlyOutput.finish();
		System.out.println( "after" );

		assertEquals( """
				written by WebObjects after its redirect
				==== Written before WOOutputPath took effect ====
				early out
				early err
				==== End of what was written before WOOutputPath took effect ====
				after
				""", file.toString( StandardCharsets.UTF_8 ) );
	}

	@Test
	public void withoutARedirectTheOriginalStreamsGoBack() {
		final PrintStream out = System.out;
		ERXEarlyOutput.start();
		System.out.println( "early" );
		ERXEarlyOutput.finish();

		assertSame( out, System.out );
		assertEquals( "early\n", _console.toString( StandardCharsets.UTF_8 ) );
	}
}
