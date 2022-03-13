package er.extensions.appserver;

import java.nio.charset.Charset;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

/**
 * Encoding utility methods.
 * 
 * FIXME Hugi 2022-03-13: We're currently maintaining a singleton and other ceremony that's not required for the currrent functionality, and probably not required at all.
 * It's purely to allow the older mechanism from Project Wonder to be re-implemented (which is probably not needed anymore)
 */

public class ERXMessageEncoding {

	private static String _defaultEncoding = "utf-8";
	private static ERXMessageEncoding SINGLETON = new ERXMessageEncoding();

	private ERXMessageEncoding() {}

	public static ERXMessageEncoding instance() {
		return SINGLETON;
	}

	public static String defaultEncoding() {
		return _defaultEncoding;
	}

	public static void setDefaultEncoding(String encoding) {
		_defaultEncoding = encoding;
	}

	public static Charset charset() {
		return Charset.forName(defaultEncoding());
	}

	public void setEncodingToResponse(WOResponse response) {
		/*
		final String mimeType = response.headerForKey("Content-Type");

		if (mimeType != null && (mimeType.equals("text/html") || mimeType.equals("text/xml"))) {
			response.setContentEncoding(defaultEncoding());
			response.setHeader(mimeType + "; charset=" + defaultEncoding(), "Content-Type");
		}
		*/
	}

	public void setDefaultFormValueEncodingToRequest(WORequest request) {
//		request.setDefaultFormValueEncoding(defaultEncoding());
	}
}