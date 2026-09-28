package er.extensions.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ERXPublicResourcesTest {

	@Test
	public void theRouteURLNamesAPathWithinTheFolder() {
		assertEquals( "robots.txt", ERXPublicResources.resourcePath( "/robots.txt" ) );
		assertEquals( ".well-known/security.txt", ERXPublicResources.resourcePath( "/.well-known/security.txt" ) );
		assertEquals( "favicon.ico", ERXPublicResources.resourcePath( "/favicon.ico?v=2" ) );
	}

	@Test
	public void thePathIsDecodedButAPlusStaysAPlus() {
		assertEquals( "my file.txt", ERXPublicResources.resourcePath( "/my%20file.txt" ) );
		assertEquals( "a+b.txt", ERXPublicResources.resourcePath( "/a+b.txt" ) );
	}

	@Test
	public void theRootAndMalformedPathsNameNoFile() {
		assertNull( ERXPublicResources.resourcePath( "/" ) );
		assertNull( ERXPublicResources.resourcePath( "" ) );
		assertNull( ERXPublicResources.resourcePath( null ) );
		assertNull( ERXPublicResources.resourcePath( "/bad%zz" ) );
	}

	@Test
	public void theIndexHoldsEveryFileByItsPathWithinTheFolder( @TempDir final Path folder ) throws IOException {
		Files.writeString( folder.resolve( "robots.txt" ), "User-agent: *" );
		Files.createDirectories( folder.resolve( ".well-known" ) );
		Files.writeString( folder.resolve( ".well-known/security.txt" ), "Contact: x" );
		Files.createDirectories( folder.resolve( "empty" ) );

		assertEquals( Set.of( "robots.txt", ".well-known/security.txt" ), ERXPublicResources.index( folder ) );
	}

	@Test
	public void noFolderMeansAnEmptyIndex() {
		assertEquals( Set.of(), ERXPublicResources.index( null ) );
	}
}
