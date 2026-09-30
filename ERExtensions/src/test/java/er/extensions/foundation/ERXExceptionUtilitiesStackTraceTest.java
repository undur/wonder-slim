package er.extensions.foundation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ERXExceptionUtilitiesStackTraceTest {

	@Test
	public void theCurrentStackStartsAtTheCaller() {
		final String trace = ERXExceptionUtilities.stackTrace();
		final String[] lines = trace.split( System.lineSeparator() );

		assertTrue( trace.startsWith( System.lineSeparator() ), "A line separator first, as before" );
		assertTrue( lines[1].startsWith( "\tat er.extensions.foundation.ERXExceptionUtilitiesStackTraceTest.theCurrentStackStartsAtTheCaller(" ), lines[1] );
		assertFalse( trace.contains( "ERXExceptionUtilities.stackTrace" ), "Its own frame is left out" );
		assertTrue( trace.endsWith( System.lineSeparator() ) );
	}
}
