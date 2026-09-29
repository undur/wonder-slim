package er.extensions.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSProperties;

/**
 * Compares each of ERXProperties' typed readers with its NSProperties counterpart over a matrix of raw values, in the
 * ways a value is read in an application: fresh, after the same key was read as another type, after the value changed
 * through the configuration, and after it changed behind the configuration's back. Values and exceptions both have to
 * match.
 *
 * Written to decide whether ERXProperties' readers could become bridges to NSProperties: with NSProperties not caching
 * (its default), they were identical but for a value spelled "-undefined-" (the "no value" marker of both) and for a
 * value changed behind the configuration's back (ERXProperties cached; NSProperties doesn't). With NSProperties caching
 * ({@code NSProperties.cacheEnabled}), reading a key as one type and then another gave wrong values or exceptions, so
 * that must stay off. Now that they're bridges, this keeps them identical.
 */
public class ERXPropertiesParityTest {

	private static final String KEY = "er.test.parity";

	/**
	 * Raw values: absent (null), empty and blank, every spelling a boolean parser might meet, numbers at and past their
	 * type's bounds, and every shape of a property list array
	 */
	static final List<String> VALUES = Arrays.asList(
			null, "", " ", "  \t ",
			"yes", "YES", "Yes", "y", "Y", "no", "NO", "n", "N", "true", "TRUE", "True", "false", "FALSE", " true ", "yes ", "on", "off",
			"0", "1", "2", "-1", "+5", "042", " 42 ", "1.5", "1e3", "0x10", "abc", "2147483647", "2147483648", "-2147483649", "9223372036854775807", "9223372036854775808",
			"(a,b)", "( a , b )", "(\"a b\", c)", "(a)", "  (a) ", "()", "(1,2,3)", "((a),b)", "(a,", "a,b", "a", "{a=b;}", "\"a\"",
			"-undefined-" );

	/**
	 * A reader: the same method on either class, by name
	 */
	record Reader( String name, Function<String, Object> erx, Function<String, Object> ns ) {}

	static final List<Reader> READERS = List.of(
			new Reader( "stringForKey", ERXProperties::stringForKey, NSProperties::stringForKey ),
			new Reader( "stringForKeyWithDefault(def)", key -> ERXProperties.stringForKeyWithDefault( key, "def" ), key -> NSProperties.stringForKeyWithDefault( key, "def" ) ),
			new Reader( "booleanForKey", ERXProperties::booleanForKey, NSProperties::booleanForKey ),
			new Reader( "booleanForKeyWithDefault(true)", key -> ERXProperties.booleanForKeyWithDefault( key, true ), key -> NSProperties.booleanForKeyWithDefault( key, true ) ),
			new Reader( "booleanForKeyWithDefault(false)", key -> ERXProperties.booleanForKeyWithDefault( key, false ), key -> NSProperties.booleanForKeyWithDefault( key, false ) ),
			new Reader( "intForKey", ERXProperties::intForKey, NSProperties::intForKey ),
			new Reader( "intForKeyWithDefault(7)", key -> ERXProperties.intForKeyWithDefault( key, 7 ), key -> NSProperties.intForKeyWithDefault( key, 7 ) ),
			new Reader( "longForKeyWithDefault(7)", key -> ERXProperties.longForKeyWithDefault( key, 7L ), key -> NSProperties.longForKeyWithDefault( key, 7L ) ),
			new Reader( "arrayForKey", ERXProperties::arrayForKey, NSProperties::arrayForKey ),
			new Reader( "arrayForKeyWithDefault((d))", key -> ERXProperties.arrayForKeyWithDefault( key, new NSArray<>( "d" ) ), key -> NSProperties.arrayForKeyWithDefault( key, new NSArray<>( "d" ) ) ) );

	@AfterEach
	public void clean() {
		System.clearProperty( KEY );
		NSProperties.sharedInstance().setCachingEnabled( false );
		clearCaches();
	}

	/**
	 * What a read gave: the value (an array as a list), or the class of the exception it threw
	 */
	static String outcome( final Function<String, Object> reader, final String key ) {
		try {
			final Object value = reader.apply( key );
			return value instanceof NSArray<?> array ? "array " + new ArrayList<>( array ) : value == null ? "null" : value.getClass().getSimpleName() + " " + value;
		}
		catch( RuntimeException e ) {
			return "threw " + e.getClass().getName();
		}
	}

	private static void set( final String value ) {
		if( value == null ) {
			System.clearProperty( KEY );
		}
		else {
			System.setProperty( KEY, value );
		}
	}

	/**
	 * What the configuration does when it changes a value: clears ERXProperties' cache, and NSProperties' through its notification
	 */
	private static void clearCaches() {
		ERXProperties.systemPropertiesChanged();
	}

	private static List<String> differences( final boolean nsCaching ) {
		NSProperties.sharedInstance().setCachingEnabled( nsCaching );
		final List<String> differences = new ArrayList<>();

		for( final String value : VALUES ) {
			for( final Reader reader : READERS ) {

				// A fresh read
				set( value );
				clearCaches();
				final String erx = outcome( reader.erx(), KEY );
				clearCaches();
				final String ns = outcome( reader.ns(), KEY );

				if( !erx.equals( ns ) ) {
					differences.add( "fresh " + reader.name() + " of " + quoted( value ) + ": ERXProperties " + erx + ", NSProperties " + ns );
				}

				// After the same key was read as another type
				for( final Reader first : READERS ) {
					set( value );
					clearCaches();
					outcome( first.erx(), KEY );
					final String erxAfter = outcome( reader.erx(), KEY );
					clearCaches();
					outcome( first.ns(), KEY );
					final String nsAfter = outcome( reader.ns(), KEY );

					if( !erxAfter.equals( nsAfter ) ) {
						differences.add( reader.name() + " after " + first.name() + " of " + quoted( value ) + ": ERXProperties " + erxAfter + ", NSProperties " + nsAfter );
					}
				}

				// After the value changed through the configuration (which clears the caches)
				for( final String newValue : List.of( "true", "5", "(x)", "changed" ) ) {
					set( value );
					clearCaches();
					outcome( reader.erx(), KEY );
					set( newValue );
					clearCaches();
					final String erxChanged = outcome( reader.erx(), KEY );

					set( value );
					clearCaches();
					outcome( reader.ns(), KEY );
					set( newValue );
					clearCaches();
					final String nsChanged = outcome( reader.ns(), KEY );

					if( !erxChanged.equals( nsChanged ) ) {
						differences.add( reader.name() + " of " + quoted( value ) + " changed to " + quoted( newValue ) + ": ERXProperties " + erxChanged + ", NSProperties " + nsChanged );
					}
				}
			}
		}

		return differences;
	}

	private static String quoted( final String value ) {
		return value == null ? "(absent)" : "'" + value + "'";
	}

	@Test
	public void theReadersAreIdenticalWithoutNSPropertiesCaching() {
		assertEquals( List.of(), differences( false ) );
	}

	/**
	 * A value changed with System.setProperty(), bypassing the configuration, so nothing is told: the value in effect is
	 * read
	 */
	@Test
	public void aValueChangedBehindTheConfigurationsBackIsReadAsItIs() {
		final Map<String, String> differences = new LinkedHashMap<>();

		for( final Reader reader : READERS ) {
			set( "1" );
			clearCaches();
			outcome( reader.erx(), KEY );
			set( "0" );
			final String erx = outcome( reader.erx(), KEY );
			final String ns = outcome( reader.ns(), KEY );

			if( !erx.equals( ns ) ) {
				differences.put( reader.name(), "ERXProperties " + erx + ", NSProperties " + ns );
			}
		}

		assertEquals( Map.of(), differences );
		assertEquals( 0, ERXProperties.intForKey( KEY ) );
	}
}
