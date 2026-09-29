package er.extensions.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.webobjects.appserver._private.WOCGIFormValues;
import com.webobjects.foundation.NSArray;

/**
 * Rewrites the form value encoder of the WebObjects jar on the test classpath
 */
public class ERXLog4jRewriteTest {

	private static byte[] encoder() throws IOException {
		try( final InputStream stream = WOCGIFormValues.class.getClassLoader().getResourceAsStream( ERXLog4jRewrite.ENCODER.replace( '.', '/' ) + ".class" ) ) {
			return stream.readAllBytes();
		}
	}

	/**
	 * Defines a class from the given bytes in a loader of its own, beneath the test's
	 */
	private static Class<?> define( final byte[] classFile ) {
		return new ClassLoader( ERXLog4jRewriteTest.class.getClassLoader() ) {
			Class<?> define() {
				return defineClass( ERXLog4jRewrite.ENCODER, classFile, 0, classFile.length );
			}
		}.define();
	}

	@Test
	public void theEncoderUsesLog4jAndTheRewrittenOneDoesnt() throws IOException {
		assertTrue( ERXLog4jRewrite.usesLog4j( encoder() ) );
		assertFalse( ERXLog4jRewrite.usesLog4j( ERXLog4jRewrite.rewrite( encoder() ) ) );
	}

	@Test
	public void theRewrittenEncoderLogsThroughSlf4jAndEncodes() throws Exception {
		final Class<?> encoderClass = define( ERXLog4jRewrite.rewrite( encoder() ) );

		final Field logger = encoderClass.getDeclaredField( "logger" );
		assertEquals( org.slf4j.Logger.class, logger.getType() );

		final Constructor<?> constructor = encoderClass.getDeclaredConstructor();
		constructor.setAccessible( true );
		final Object encoder = constructor.newInstance();

		final Map<String, Object> values = new LinkedHashMap<>();
		values.put( "a", "1" );
		values.put( "b", "x y&z" );
		values.put( "c", new NSArray<>( new String[] { "p", "q" } ) );

		// Encodes as WebObjects' own does (log4j is on the test classpath, so the original runs here)
		final Object original = WOCGIFormValues.getInstance().encoder();

		for( final boolean escape : new boolean[] { true, false } ) {
			final Method encode = encoderClass.getMethod( "encodeAsCGIFormValues", Map.class, String.class, boolean.class );
			final Method originalEncode = original.getClass().getMethod( "encodeAsCGIFormValues", Map.class, String.class, boolean.class );
			assertEquals( originalEncode.invoke( original, values, "&", escape ), encode.invoke( encoder, values, "&", escape ) );
		}

		assertEquals( original.getClass().getMethod( "encodeAsCGIFormValues", Map.class, String.class ).invoke( original, values, "&" ), encoderClass.getMethod( "encodeAsCGIFormValues", Map.class, String.class ).invoke( encoder, values, "&" ) );

		logger.setAccessible( true );
		assertEquals( org.slf4j.LoggerFactory.getLogger( encoderClass ), logger.get( null ) );
	}
}
