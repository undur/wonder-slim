package er.extensions.appserver;

import java.util.Enumeration;
import java.util.Map;

import com.webobjects.appserver.WOMessage;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableDictionary;

import er.extensions.foundation.ERXSimpleTemplateParser;

/**
 * Holds encoding related settings and methods for {@link WOMessage} and its subclasses {@link WORequest} and {@link WOResponse}.
 */

public class ERXMessageEncoding {

	private String _encoding;
	private static Map<String, String> _encodings;
	private static Map<String, String> _languagesAndDefaultEncodings;

	private String encoding() {
		return _encoding;
	}

	public ERXMessageEncoding(String languageOrEncoding) {
		if (availableEncodings().containsObject(languageOrEncoding)) {
			_encoding = languageOrEncoding;
		}
		else if (availableLanguages().containsObject(languageOrEncoding)) {
			_encoding = defaultEncodingForLanguage(languageOrEncoding);
		}
		else {
			_encoding = defaultEncoding();
		}
	}

	public ERXMessageEncoding(NSArray preferedLanguages) {
		_encoding = null;
		NSArray availableLanguages = availableLanguages();

		Enumeration e = preferedLanguages.objectEnumerator();
		while (e.hasMoreElements()) {
			String aPreferedLanguage = (String) e.nextElement();
			if (availableLanguages.containsObject(aPreferedLanguage)) {
				_encoding = defaultEncodingForLanguage(aPreferedLanguage);
				break;
			}
		}
		if (_encoding == null) {
			_encoding = defaultEncoding();
		}
	}

	private static NSArray availableEncodings() {
		return new NSArray<>(_encodings().keySet());
	}

	private static NSArray availableLanguages() {
		return new NSArray<>(_languagesAndDefaultEncodings().keySet());
	}

	private static Map<String, String> _encodings() {
		if (_encodings == null) {
			_encodings = Map.of(
					"ISO8859_1", "ISO-8859-1",
					"ISO-8859-1", "ISO-8859-1",
					"SJIS", "Shift_JIS",
					"SHIFT_JIS", "Shift_JIS",
					"EUC_JP", "EUC-JP", // Note: dash and underscore
					"EUC-JP", "EUC-JP",
					"ISO2022JP", "iso-2022-jp",
					"ISO-2022-JP", "iso-2022-jp",
					"UTF8", "UTF-8",
					"UTF-8", "UTF-8");
		}
		return _encodings;
	}


	private static Map<String, String> _languagesAndDefaultEncodings() {
		if (_languagesAndDefaultEncodings == null) {
			_languagesAndDefaultEncodings = Map.of(
					"English", "ISO8859_1",
					"German", "ISO8859_1",
					"Japanese", "SJIS");
		}
		return _languagesAndDefaultEncodings;
	}

	private static void _setLanguagesAndDefaultEncodings(NSDictionary newLanguagesAndDefaultEncodings) {
		_languagesAndDefaultEncodings = newLanguagesAndDefaultEncodings;
	}

	private static String _defaultEncoding;

	public static String defaultEncoding() {

		if (_defaultEncoding == null) {
			_defaultEncoding = "ISO8859_1"; // WTF? No!
		}

		return _defaultEncoding;
	}

	public static void setDefaultEncoding(String newDefaultEncoding) {

		if (!availableEncodings().containsObject(newDefaultEncoding.toUpperCase())) {
			throw createIllegalArgumentException(newDefaultEncoding, "encoding", "availableEncodings()");
		}

		_defaultEncoding = newDefaultEncoding;
	}

	public static void setDefaultEncodingForAllLanguages(String newDefaultEncoding) {
		// This statement may throw an IllegalArgumentException when
		// newDefaultEncoding isn't supported.
		setDefaultEncoding(newDefaultEncoding);

		final NSMutableDictionary d = new NSMutableDictionary(_languagesAndDefaultEncodings());
		final Enumeration e = d.keyEnumerator();

		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			d.setObjectForKey(newDefaultEncoding, key);
		}

		_setLanguagesAndDefaultEncodings(d);
	}

	private static String defaultEncodingForLanguage(String language) {
		String defaultEncoding = null;

		if (availableLanguages().containsObject(language)) {
			defaultEncoding = _languagesAndDefaultEncodings.get(language);
		}

		if (defaultEncoding == null) {
			defaultEncoding = defaultEncoding();
		}

		return defaultEncoding;
	}

	private static void setEncodingToResponse(WOResponse response, String encoding) {
		encoding = encoding.toUpperCase();

		if (!availableEncodings().containsObject(encoding)) {
			throw createIllegalArgumentException(encoding, "encoding", "availableEncodings()");
		}

		final String mimeType = response.headerForKey("Content-Type");

		if (mimeType != null && (mimeType.equals("text/html") || mimeType.equals("text/xml"))) {
			response.setContentEncoding(encoding);
			response.setHeader(mimeType + "; charset=" + _encodings().get(encoding), "Content-Type");
		}
	}

	public void setEncodingToResponse(WOResponse response) {
		setEncodingToResponse(response, encoding());
	}

	private static void setDefaultFormValueEncodingToRequest(WORequest request, String encoding) {
		encoding = encoding.toUpperCase();

		if (!availableEncodings().containsObject(encoding)) {
			throw createIllegalArgumentException(encoding, "encoding", "availableEncodings()");
		}

		request.setDefaultFormValueEncoding(encoding);
	}

	public void setDefaultFormValueEncodingToRequest(WORequest request) {
		setDefaultFormValueEncodingToRequest(request, encoding());
	}

	private static IllegalArgumentException createIllegalArgumentException(String value, String target, String listingMethod) {
		Map d = Map.of(
				"value", value,
				"target", target,
				"listingMethod", listingMethod);

		ERXSimpleTemplateParser parser = ERXSimpleTemplateParser.sharedInstance();
		String message = parser.parseTemplateWithObject("@@value@@ isn't a supported @@target@@. (Not listed under @@listingMethod@@)", null, d, null);
		return new IllegalArgumentException(message);
	}

	@Override
	public String toString() {
		return "<" + getClass().getName() + " encoding: " + _encoding + ">";
	}
}