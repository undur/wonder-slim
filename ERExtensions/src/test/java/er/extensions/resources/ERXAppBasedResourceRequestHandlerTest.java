package er.extensions.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ERXAppBasedResourceRequestHandlerTest {

	private static int status( final String path ) {
		return new ERXAppBasedResourceRequestHandler().responseForPath( path ).status();
	}

	@Test
	public void aPathThatNamesNoResourceIsNotFound() {
		assertEquals( 404, status( null ) );
		assertEquals( 404, status( "" ) );
		assertEquals( 404, status( "foo" ) );
		assertEquals( 404, status( "/foo" ) );
		assertEquals( 404, status( "app/" ) );
		assertEquals( 404, status( "app/public/" ) );
	}
}
