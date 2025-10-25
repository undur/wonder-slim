package er.extensions.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class TestRouteURL {

	@Test
	public void howTheHeckToHandleDoubleSlashes() {
		// FIXME: Implement. Empty path component or eliminate path component? // Hugi 2025-10-11
	}

	@Test
	public void removesSlashAtStartAndEnd() {
		RouteURL url = RouteURL.create( "/url/" );
		assertEquals( url.toString(), "url" );
	}

	@Test
	public void length() {
		// 1 element length urls
		assertEquals( 1, RouteURL.create( "/url/" ).length() );
		assertEquals( 1, RouteURL.create( "/url" ).length() );
		assertEquals( 1, RouteURL.create( "url/" ).length() );
		assertEquals( 1, RouteURL.create( "url" ).length() );
		
		// 2 element length urls
		assertEquals( 2, RouteURL.create( "/url/bork/" ).length() );
		assertEquals( 2, RouteURL.create( "/url/bork" ).length() );
		assertEquals( 2, RouteURL.create( "url/bork/" ).length() );
		assertEquals( 2, RouteURL.create( "url/bork" ).length() );
	}

	@Test
	public void integerValue() {
		RouteURL url = RouteURL.create( "/url/2/haha" );
		assertEquals( url.getInteger( 1 ), Integer.valueOf( 2 ) );
	}

	@Test
	public void stringValue() {
		RouteURL url = RouteURL.create( "/url/2/haha" );
		assertEquals( url.getString( 0 ), "url" );
		assertEquals( url.getString( 1 ), "2" );
		assertEquals( url.getString( 2 ), "haha" );
	}

	@Test
	public void stringValueExceedingLengthIsNull() {
		RouteURL url = RouteURL.create( "/url/2/haha" );
		assertNull( url.getString( 4 ) );
	}

	@Test
	public void integerValueExceedingLengthIsNull() {
		RouteURL url = RouteURL.create( "/url/2/haha" );
		assertNull( url.getInteger( 4 ) );
	}
}