package er.extensions.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.webobjects.foundation.NSArray;

/**
 * Pins what each of ERXProperties' typed readers returns for a matrix of raw values, recorded from its implementation
 * before its readers became bridges to NSProperties (erxproperties-pinned.txt). A difference here is a value an
 * application reads differently, so it's never updated to make a test pass; only on purpose, with the reason in the
 * commit.
 */
public class ERXPropertiesPinnedTest {

	private static final String KEY = "er.test.pinned";

	static final List<String> VALUES = ERXPropertiesParityTest.VALUES;

	/**
	 * A reader of ERXProperties, by name
	 */
	record PinnedReader( String name, Function<String, Object> reader ) {}

	static final List<PinnedReader> READERS = List.of(
			new PinnedReader( "stringForKey", ERXProperties::stringForKey ),
			new PinnedReader( "stringForKeyWithDefault(def)", key -> ERXProperties.stringForKeyWithDefault( key, "def" ) ),
			new PinnedReader( "booleanForKey", ERXProperties::booleanForKey ),
			new PinnedReader( "booleanForKeyWithDefault(true)", key -> ERXProperties.booleanForKeyWithDefault( key, true ) ),
			new PinnedReader( "booleanForKeyWithDefault(false)", key -> ERXProperties.booleanForKeyWithDefault( key, false ) ),
			new PinnedReader( "intForKey", ERXProperties::intForKey ),
			new PinnedReader( "intForKeyWithDefault(7)", key -> ERXProperties.intForKeyWithDefault( key, 7 ) ),
			new PinnedReader( "longForKeyWithDefault(7)", key -> ERXProperties.longForKeyWithDefault( key, 7L ) ),
			new PinnedReader( "arrayForKey", ERXProperties::arrayForKey ),
			new PinnedReader( "arrayForKeyWithDefault((d))", key -> ERXProperties.arrayForKeyWithDefault( key, new NSArray<>( "d" ) ) ),
			new PinnedReader( "bigDecimalForKeyWithDefault(10)", key -> ERXProperties.bigDecimalForKeyWithDefault( key, BigDecimal.TEN ) ),
			new PinnedReader( "enumValueForKey(TimeUnit)", key -> ERXProperties.enumValueForKey( TimeUnit.class, key ) ) );

	@AfterEach
	public void clean() {
		System.clearProperty( KEY );
		ERXProperties.systemPropertiesChanged();
	}

	/**
	 * @return A line of the pinned file: the reader, the raw value (escaped, "(absent)" for none) and what the read gave
	 */
	static String line( final PinnedReader reader, final String value ) {

		if( value == null ) {
			System.clearProperty( KEY );
		}
		else {
			System.setProperty( KEY, value );
		}

		ERXProperties.systemPropertiesChanged();
		final String outcome = ERXPropertiesParityTest.outcome( reader.reader(), KEY );
		System.clearProperty( KEY );
		ERXProperties.systemPropertiesChanged();
		return reader.name() + "\t" + ( value == null ? "(absent)" : "'" + value.replace( "\t", "\\t" ) + "'" ) + "\t" + outcome;
	}

	@Test
	public void theReadersReturnWhatTheyAlwaysHave() throws IOException {
		final List<String> pinned;

		try( InputStream stream = getClass().getResourceAsStream( "erxproperties-pinned.txt" ) ) {
			pinned = new String( stream.readAllBytes(), StandardCharsets.UTF_8 ).lines().toList();
		}

		final List<String> actual = new ArrayList<>();

		for( final String value : VALUES ) {
			for( final PinnedReader reader : READERS ) {
				actual.add( line( reader, value ) );
			}
		}

		assertEquals( pinned, actual );
	}
}
