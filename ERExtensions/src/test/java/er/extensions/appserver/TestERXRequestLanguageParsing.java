package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.webobjects.foundation.NSArray;

public class TestERXRequestLanguageParsing {

	private static NSArray<String> arr(String... s) {
		return new NSArray<String>(s);
	}

	@Test
	public void simpleSort() {
		// Higher q comes first
		final NSArray<String> sorted = ERXRequest.fixAbbreviationArray(arr("en;q=0.5", "de;q=0.9"));
		assertEquals("de", sorted.objectAtIndex(0));
		assertEquals("en", sorted.objectAtIndex(1));
	}

	@Test
	public void defaultQualityIsOne() {
		// No ;q= means q=1.0, which beats ;q=0.9
		final NSArray<String> sorted = ERXRequest.fixAbbreviationArray(arr("en;q=0.9", "de"));
		assertEquals("de", sorted.objectAtIndex(0));
	}

	@Test
	public void duplicateQDoesNotThrow() {
		// Real-world malformed header — duplicate ;q= segments. Sort must not throw.
		final NSArray<String> sorted = ERXRequest.fixAbbreviationArray(arr("en-US", "en;q=0.9;q=0.9"));
		// Result order doesn't matter much here, just that the call completes
		assertEquals(2, sorted.count());
	}

	@Test
	public void malformedQFallsBackToOne() {
		// Garbage in the q value shouldn't throw — treat as default 1.0
		final NSArray<String> sorted = ERXRequest.fixAbbreviationArray(arr("en;q=garbage", "de;q=0.5"));
		// "en" (treated as 1.0) should sort before "de" (0.5)
		assertEquals("en", sorted.objectAtIndex(0));
	}

	@Test
	public void emptyQFallsBackToOne() {
		final NSArray<String> sorted = ERXRequest.fixAbbreviationArray(arr("en;q=", "de;q=0.5"));
		assertEquals("en", sorted.objectAtIndex(0));
	}

	@Test
	public void noQAndDuplicateQMix() {
		final NSArray<String> sorted = ERXRequest.fixAbbreviationArray(arr("fr;q=0.3", "en-US", "en;q=0.9;q=0.9"));
		// fr should come last (lowest q)
		assertTrue(sorted.indexOfObject("fr") > sorted.indexOfObject("en-US"));
	}
}
