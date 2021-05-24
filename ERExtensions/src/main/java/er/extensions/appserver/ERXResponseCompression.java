package er.extensions.appserver;

import java.io.InputStream;

import org.apache.log4j.Logger;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSSet;

import er.extensions.foundation.ERXCompressionUtilities;
import er.extensions.foundation.ERXProperties;

/**
 * Hosts the response compression logic previously found in ERXApplication 
 */

public class ERXResponseCompression {

	private static final Logger log = Logger.getLogger(ERXResponseCompression.class);

	private static NSSet<String> _responseCompressionTypes;
	private static Boolean _responseCompressionEnabled;

	/**
	 * checks the value of
	 * <code>er.extensions.ERXApplication.responseCompressionTypes</code> for
	 * mime types that allow response compression in addition to text/* types.
	 * The default is ("application/x-javascript")
	 * 
	 * @return an array of mime type strings
	 * 
	 * FIXME: Rename the properties to reflect the class name change
	 */
	public static NSSet<String> responseCompressionTypes() {
		if (_responseCompressionTypes == null) {
			_responseCompressionTypes = new NSSet<>(ERXProperties.arrayForKeyWithDefault("er.extensions.ERXApplication.responseCompressionTypes", new NSArray<String>("application/x-javascript")));
		}

		return _responseCompressionTypes;
	}

	/**
	 * checks the value of
	 * <code>er.extensions.ERXApplication.responseCompressionEnabled</code> and
	 * if true turns on response compression by gzip
	 * 
	 * FIXME: Rename the properties to reflect the class name change
	 */
	public static boolean responseCompressionEnabled() {
		if (_responseCompressionEnabled == null) {
			_responseCompressionEnabled = ERXProperties.booleanForKeyWithDefault("er.extensions.ERXApplication.responseCompressionEnabled", false) ? Boolean.TRUE : Boolean.FALSE;
		}

		return _responseCompressionEnabled.booleanValue();
	}
	
	public static boolean shouldCompress( final WORequest request, final WOResponse response ) {
		final String contentType = response.headerForKey("content-type");
		String acceptEncoding = request.headerForKey("accept-encoding");

		final boolean contentTypeCheck = !"gzip".equals(response.headerForKey("content-encoding")) && (contentType != null) && (contentType.startsWith("text/") || responseCompressionTypes().containsObject(contentType));
		final boolean acceptEncodingCheck = (acceptEncoding != null) && (acceptEncoding.toLowerCase().indexOf("gzip") != -1);
		
		return contentTypeCheck && acceptEncodingCheck;
	}

	public static WOResponse compressResponse( final WOResponse response ) {
	
		long start = System.currentTimeMillis();
		long inputBytesLength;
		InputStream contentInputStream = response.contentInputStream();
		NSData compressedData;
		if (contentInputStream != null) {
			inputBytesLength = response.contentInputStreamLength();
			compressedData = ERXCompressionUtilities.gzipInputStreamAsNSData(contentInputStream, (int) inputBytesLength);
			response.setContentStream(null, 0, 0);
		}
		else {
			NSData input = response.content();
			inputBytesLength = input.length();
			if (inputBytesLength > 0) {
				compressedData = ERXCompressionUtilities.gzipByteArrayAsNSData(input._bytesNoCopy(), 0, (int) inputBytesLength);
			}
			else {
				compressedData = NSData.EmptyData;
			}
		}
		if (inputBytesLength > 0) {
			if (compressedData == null) {
				// something went wrong
			}
			else {
				response.setContent(compressedData);
				response.setHeader(String.valueOf(compressedData.length()), "content-length");
				response.setHeader("gzip", "content-encoding");

				if (log.isDebugEnabled()) {
					log.debug("before: " + inputBytesLength + ", after " + compressedData.length() + ", time: " + (System.currentTimeMillis() - start));
				}
			}
		}
		
		return response;
	}
}