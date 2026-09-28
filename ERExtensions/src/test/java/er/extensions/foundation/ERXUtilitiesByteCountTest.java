package er.extensions.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * formatByteCount() formats in the current locale; with none set (as here), the JVM's default, which the tests set
 */
public class ERXUtilitiesByteCountTest {

	private Locale _defaultLocale;

	@BeforeEach
	public void rememberLocale() {
		_defaultLocale = Locale.getDefault();
	}

	@AfterEach
	public void restoreLocale() {
		Locale.setDefault( _defaultLocale );
	}

	@Test
	public void english() {
		Locale.setDefault( Locale.ENGLISH );
		assertEquals( "0 B", ERXUtilities.formatByteCount( 0 ) );
		assertEquals( "123 B", ERXUtilities.formatByteCount( 123 ) );
		assertEquals( "999 B", ERXUtilities.formatByteCount( 999 ) );
		assertEquals( "1 KB", ERXUtilities.formatByteCount( 1000 ) );
		assertEquals( "1.5 KB", ERXUtilities.formatByteCount( 1536 ) );
		assertEquals( "58.9 KB", ERXUtilities.formatByteCount( 58_900 ) );
		assertEquals( "1.2 MB", ERXUtilities.formatByteCount( 1_234_567 ) );
		assertEquals( "1.2 GB", ERXUtilities.formatByteCount( 1_234_567_890L ) );
		assertEquals( "3.5 TB", ERXUtilities.formatByteCount( 3_500_000_000_000L ) );
	}

	@Test
	public void roundingUpMovesToTheNextUnit() {
		Locale.setDefault( Locale.ENGLISH );
		assertEquals( "1 MB", ERXUtilities.formatByteCount( 999_960 ) );
		assertEquals( "999.9 KB", ERXUtilities.formatByteCount( 999_940 ) );
	}

	@Test
	public void icelandic() {
		Locale.setDefault( Locale.of( "is" ) );
		assertEquals( "1,2 GB", ERXUtilities.formatByteCount( 1_234_567_890L ) );
		assertEquals( "58,9 KB", ERXUtilities.formatByteCount( 58_900 ) );
	}

	@Test
	public void negative() {
		Locale.setDefault( Locale.ENGLISH );
		assertEquals( "-1.5 KB", ERXUtilities.formatByteCount( -1536 ) );
	}
}
