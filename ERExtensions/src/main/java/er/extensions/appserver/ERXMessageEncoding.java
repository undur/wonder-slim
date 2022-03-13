package er.extensions.appserver;

import java.nio.charset.Charset;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;

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

	private static void setEncodingToResponse(WOResponse response, String encoding) {

		final String mimeType = response.headerForKey("Content-Type");

		if (mimeType != null && (mimeType.equals("text/html") || mimeType.equals("text/xml"))) {
			response.setContentEncoding(encoding);
			response.setHeader(mimeType + "; charset=" + defaultEncoding(), "Content-Type");
		}
	}

	public void setEncodingToResponse(WOResponse response) {
		setEncodingToResponse(response, defaultEncoding());
	}

	private static void setDefaultFormValueEncodingToRequest(WORequest request, String encoding) {
		request.setDefaultFormValueEncoding(encoding);
	}

	public void setDefaultFormValueEncodingToRequest(WORequest request) {
		setDefaultFormValueEncodingToRequest(request, defaultEncoding());
	}
}