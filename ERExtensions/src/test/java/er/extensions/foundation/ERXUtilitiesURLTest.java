package er.extensions.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;

public class ERXUtilitiesURLTest {

	@Test
	public void appendQueryParameter() {
		assertEquals( "/ajax/0.1.3?_r=true", ERXUtilities.appendQueryParameter( "/ajax/0.1.3", "_r", "true" ) );
		assertEquals( "/cgi-bin/WebObjects/App.woa/ajax/0.1.3?_r=true", ERXUtilities.appendQueryParameter( "/cgi-bin/WebObjects/App.woa/ajax/0.1.3", "_r", "true" ) );
		assertEquals( "/ajax/0.1.3?_u=container&_r=true", ERXUtilities.appendQueryParameter( "/ajax/0.1.3?_u=container", "_r", "true" ) );
		assertEquals( "http://host:8080/wa/x?a=b&k=v+w#frag", ERXUtilities.appendQueryParameter( "http://host:8080/wa/x?a=b#frag", "k", "v w" ) );
	}

	@Test
	public void appendKeyOnly() {
		assertEquals( "https://host/wa/modal?1727400000000", ERXUtilities.appendQueryParameter( "https://host/wa/modal", "1727400000000", null ) );
	}

	@Test
	public void appendAfterAnEscapedSeparator() {
		assertEquals( "https://host/cgi-bin/WebObjects/App.woa/wa/modal?x=1&y=2&1727400000000", ERXUtilities.appendQueryParameter( "https://host/cgi-bin/WebObjects/App.woa/wa/modal?x=1&amp;y=2", "1727400000000", null ) );
	}

	@Test
	public void queryString() {
		assertEquals( "a=1", ERXUtilities.queryString( new NSDictionary<>( "1", "a" ) ) );
		assertEquals( "c=1&c=2", ERXUtilities.queryString( new NSDictionary<>( new NSArray<>( new String[] { "1", "2" } ), "c" ) ) );

		final NSMutableDictionary<String, Object> d = new NSMutableDictionary<>();
		d.setObjectForKey( "x y", "a" );
		d.setObjectForKey( "ä&=/", "b" );
		assertEquals( Set.of( "a=x+y", "b=%C3%A4%26%3D%2F" ), Set.of( ERXUtilities.queryString( d ).split( "&" ) ) );
	}

	@Test
	public void emptyQueryString() {
		assertNull( ERXUtilities.queryString( null ) );
		assertNull( ERXUtilities.queryString( NSDictionary.emptyDictionary() ) );
	}
}
