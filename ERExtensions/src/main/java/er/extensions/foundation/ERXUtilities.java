/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.foundation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation.NSSelector;

import er.extensions.appserver.ERXLocale;

public class ERXUtilities {

	private static final Class[] NotificationClassArray = { com.webobjects.foundation.NSNotification.class };

	/**
	 * @return A selector suitable for firing a notification
	 */
	public static NSSelector<Void> notificationSelector(String methodName) {
		return new NSSelector<>(methodName, ERXUtilities.NotificationClassArray);
	}

	/**
	 * Read and parse a string plist resource
	 *
	 * @param filename name of the file
	 * @param frameworkName name of framework containing resource, <code>null</code> or 'app' for the application bundle
	 * @param languages language list search order
	 * @param encoding string encoding of the resource
	 * 
	 * @return de-serialized plist from the resource
	 */
	public static Object readPListFromBundleResource(final String filename, final String frameworkName, final NSArray<String> languages, Charset charset) {
		final String plistString = readStringFromBundleResource( filename, frameworkName, languages, charset);
		return NSPropertyListSerialization.propertyListFromString(plistString);
	}

	/**
	 * Read a string resource 
	 *
	 * @param filename name of the file
	 * @param frameworkName name of framework containing resource, <code>null</code> or 'app' for the application bundle
	 * @param languages language list search order
	 * @param charset string Charset of the resource
	 * 
	 * @return string content of the resource
	 */
	public static String readStringFromBundleResource(final String filename, final String frameworkName, final NSArray<String> languages, Charset charset) {
		try( final InputStream stream = WOApplication.application().resourceManager().inputStreamForResourceNamed(filename, frameworkName, languages)) {
			
			if( stream == null ) {
				return null;
			}
			
			return new String(stream.readAllBytes(), charset);
		}
		catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	/**
	 * @return true if [string] is null or empty
	 */
	public static boolean stringIsNullOrEmpty( String string ) {
		return string == null || string.isEmpty();
	}

	/**
	 * @return The URL with a query parameter appended, after a '?' or '&amp;' as the URL requires, and before a fragment
	 *         (#...). The key and value are URL-encoded; a null value appends the key alone. An "&amp;amp;" in the URL (an
	 *         HTML-escaped separator) becomes "&amp;".
	 */
	public static String appendQueryParameter( final String url, final String key, final String value ) {
		String result = url.replace( "&amp;", "&" );
		String fragment = "";
		final int fragmentStart = result.indexOf( '#' );

		if( fragmentStart != -1 ) {
			fragment = result.substring( fragmentStart );
			result = result.substring( 0, fragmentStart );
		}

		final StringBuilder sb = new StringBuilder( result );

		if( result.indexOf( '?' ) == -1 ) {
			sb.append( '?' );
		}
		else if( !result.endsWith( "?" ) && !result.endsWith( "&" ) ) {
			sb.append( '&' );
		}

		appendQueryParameter( sb, key, value );
		sb.append( fragment );
		return sb.toString();
	}

	/**
	 * @return The dictionary as a URL query string (a=1&amp;b=2, without a leading '?'), keys and values URL-encoded, in the
	 *         dictionary's key order. An NSArray value gives the key once per element. Null for no parameters.
	 */
	public static String queryString( final NSDictionary<String, ? extends Object> parameters ) {
		if( parameters == null || parameters.isEmpty() ) {
			return null;
		}

		final StringBuilder sb = new StringBuilder();

		for( final String key : parameters.allKeys() ) {
			final Object value = parameters.objectForKey( key );
			final List<?> values = value instanceof NSArray<?> array ? array : List.of( value );

			for( final Object element : values ) {
				if( sb.length() > 0 ) {
					sb.append( '&' );
				}

				appendQueryParameter( sb, key, element.toString() );
			}
		}

		return sb.toString();
	}

	private static void appendQueryParameter( final StringBuilder sb, final String key, final String value ) {
		sb.append( URLEncoder.encode( key, StandardCharsets.UTF_8 ) );

		if( value != null ) {
			sb.append( '=' );
			sb.append( URLEncoder.encode( value, StandardCharsets.UTF_8 ) );
		}
	}

	/**
	 * Units for {@link #formatByteCount(long)}, each 1000 times the previous
	 */
	private static final String[] BYTE_UNITS = { "B", "KB", "MB", "GB", "TB", "PB", "EB" };

	/**
	 * @return A byte count for people to read: "123 B", "1.5 KB", "1.2 GB", in steps of 1000, with one decimal. The
	 *         number is formatted in the current locale ({@link ERXLocale#current()}, or the JVM's default where none is
	 *         set), so it's "1,2 GB" in Icelandic.
	 */
	public static String formatByteCount( final long bytes ) {
		double value = bytes;
		int unit = 0;

		// Move up a unit while the value, as displayed (one decimal), would be 1000 or more
		while( unit < BYTE_UNITS.length - 1 && Math.abs( Math.round( value * 10 ) / 10.0 ) >= 1000 ) {
			value = value / 1000;
			unit++;
		}

		final Locale locale = ERXLocale.current() != null ? ERXLocale.current() : Locale.getDefault();
		final NumberFormat format = NumberFormat.getNumberInstance( locale );
		format.setMaximumFractionDigits( unit == 0 ? 0 : 1 );
		return format.format( value ) + " " + BYTE_UNITS[unit];
	}
}
