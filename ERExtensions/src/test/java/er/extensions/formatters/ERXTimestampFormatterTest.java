package er.extensions.formatters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


import org.junit.jupiter.api.Test;

import com.webobjects.foundation.NSTimeZone;
import com.webobjects.foundation.NSTimestamp;
import com.webobjects.foundation.NSTimestampFormatter;

public class ERXTimestampFormatterTest {

	@Test
	public void defaultFormatterRendersTheFullTimestamp() {
		final NSTimestampFormatter format = (NSTimestampFormatter)ERXTimestampFormatter.defaultDateFormatterForObject( new NSTimestamp() );
		format.setDefaultFormatTimeZone( NSTimeZone.timeZoneWithName( "GMT", true ) );

		// 2026-09-27 12:34:56 GMT
		assertEquals( "2026-09-27 12:34:56 Etc/GMT", format.format( new NSTimestamp( 1790512496000L ) ) );
	}

	@Test
	public void noDefaultFormatterForOtherValues() {
		assertNull( ERXTimestampFormatter.defaultDateFormatterForObject( null ) );
		assertNull( ERXTimestampFormatter.defaultDateFormatterForObject( "2026-09-27" ) );
		assertNull( ERXTimestampFormatter.defaultDateFormatterForObject( 42 ) );
	}
}
