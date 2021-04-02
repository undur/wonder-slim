package er.extensions.foundation;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.foundation.NSData;

public class ERXCompressionUtilities {

	private static final Logger log = LoggerFactory.getLogger(ERXCompressionUtilities.class);

	/**
	 * Returns an NSData containing the gzipped version of the given input stream.
	 * 
	 * @param input the input stream to compress
	 * @param length the length of the input stream
	 * @return gzipped NSData
	 */
	public static NSData gzipInputStreamAsNSData(InputStream input, int length) {
		try( ERXRefByteArrayOutputStream bos = new ERXRefByteArrayOutputStream(length)) {
			if (input != null) {
				try( GZIPOutputStream out = new GZIPOutputStream(bos)) {
					ERXFileUtilities.writeInputStreamToOutputStream(input, true, out, false);
				}
			}
			return bos.toNSData();
		}
		catch (IOException e) {
			log.error("Failed to gzip byte array.", e);
			return null;
		}
	}

	public static NSData gzipByteArrayAsNSData(byte[] input, int offset, int length) {
		try( ERXRefByteArrayOutputStream bos = new ERXRefByteArrayOutputStream(length)) {
			if (input != null) {
				try( GZIPOutputStream out = new GZIPOutputStream(bos)) {
					out.write(input, offset, length);
					out.finish();
				}
			}
			return bos.toNSData();
		}
		catch (IOException e) {
			log.error("Failed to gzip byte array.", e);
			return null;
		}
	}
}