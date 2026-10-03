package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Static members a key path's first key can name (#172)
 */
public class ERXKeyValueAssociationTest {

	public interface Constants {
		String fromInterface = "interface";
	}

	public static class Base implements Constants {
		public static final String fromSuperclass = "superclass";

		public static String fromMethod() {
			return "method";
		}

		static final String notPublic = "hidden";

		public final String instanceOnly = "instance";
	}

	public static class Page extends Base {}

	private static Object read( final String key ) {
		return ERXKeyValueAssociation.staticReader( Page.class, key ).map( reader -> reader.apply( null ) ).orElse( null );
	}

	@Test
	public void aKeyReachesAStaticMemberOfTheClassItsSuperclassesOrInterfaces() {
		assertEquals( "interface", read( "fromInterface" ) );
		assertEquals( "superclass", read( "fromSuperclass" ) );
		assertEquals( "method", read( "fromMethod" ) );
	}

	@Test
	public void onlyPublicStaticMembers() {
		assertTrue( ERXKeyValueAssociation.staticReader( Page.class, "notPublic" ).isEmpty() );
		assertTrue( ERXKeyValueAssociation.staticReader( Page.class, "instanceOnly" ).isEmpty() );
		assertTrue( ERXKeyValueAssociation.staticReader( Page.class, "nothing" ).isEmpty() );
	}
}
