package er.extensions;

/**
 * Temporary exception class added to mark the places that need fixup in the
 * Wonder/Undur migration process.
 * 
 * I should not exist, but here I am.
 */

public class FIXMEException extends RuntimeException {

	public FIXMEException() {
		super();
	}

	public FIXMEException(String string) {
		super(string);
	}
}