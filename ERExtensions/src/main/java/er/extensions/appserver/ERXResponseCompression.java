package er.extensions.appserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSRange;
import com.webobjects.foundation.NSSet;

import er.extensions.foundation.ERXFileUtilities;
import er.extensions.foundation.ERXProperties;

/**
 * Hosts the response compression logic previously found in ERXApplication 
 */

public class ERXResponseCompression {

	private static final Logger log = LoggerFactory.getLogger(ERXResponseCompression.class);

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

	/**
	 * Checks headers on the request and response
	 * 
	 * FIXME: clean up those checks a bit to make it easier to see what's happening.
	 */
	public static boolean shouldCompress( final WORequest request, final WOResponse response ) {
		final String contentType = response.headerForKey("content-type");
		final String contentEncoding = response.headerForKey("content-encoding");
		final String acceptEncoding = request.headerForKey("accept-encoding");

		final boolean contentTypeCheck = !"gzip".equals(contentEncoding) && (contentType != null) && (contentType.startsWith("text/") || responseCompressionTypes().containsObject(contentType));
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
	
	private static class ERXCompressionUtilities {

		/**
		 * Returns an NSData containing the gzipped version of the given input stream.
		 * 
		 * @param input the input stream to compress
		 * @param length the length of the input stream
		 * @return gzipped NSData
		 */
		private static NSData gzipInputStreamAsNSData(InputStream input, int length) {
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

		private static NSData gzipByteArrayAsNSData(byte[] input, int offset, int length) {
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

		/**
		 * This class is uh ... inspired ... by ByteArrayOutputStream, except
		 * that it gives direct access to the underlying byte buffer for 
		 * performing operations on the buffer without a byte array copying
		 * penalty.
		 *
		 * @author  Arthur van Hoff
		 * @author mschrag
		 */
		private static class ERXRefByteArrayOutputStream extends OutputStream {

		  /** 
		   * The buffer where data is stored. 
		   */
		  protected byte buf[];

		  /**
		   * The number of valid bytes in the buffer. 
		   */
		  protected int count;

		  /**
		   * Creates a new byte array output stream. The buffer capacity is 
		   * initially 32 bytes, though its size increases if necessary. 
		   */
		  public ERXRefByteArrayOutputStream() {
		    this(32);
		  }

		  /**
		   * Creates a new byte array output stream, with a buffer capacity of 
		   * the specified size, in bytes. 
		   *
		   * @param   size   the initial size.
		   * @exception  IllegalArgumentException if size is negative.
		   */
		  public ERXRefByteArrayOutputStream(int size) {
		    if (size < 0) {
		      throw new IllegalArgumentException("Negative initial size: " + size);
		    }
		    buf = new byte[size];
		  }

		  /**
		   * Writes the specified byte to this byte array output stream. 
		   *
		   * @param   b   the byte to be written.
		   */
		  @Override
		  public synchronized void write(int b) {
		    int newcount = count + 1;
		    if (newcount > buf.length) {
		      byte newbuf[] = new byte[Math.max(buf.length << 1, newcount)];
		      System.arraycopy(buf, 0, newbuf, 0, count);
		      buf = newbuf;
		    }
		    buf[count] = (byte) b;
		    count = newcount;
		  }

		  /**
		   * Writes <code>len</code> bytes from the specified byte array 
		   * starting at offset <code>off</code> to this byte array output stream.
		   *
		   * @param   b     the data.
		   * @param   off   the start offset in the data.
		   * @param   len   the number of bytes to write.
		   */
		  @Override
		  public synchronized void write(byte b[], int off, int len) {
		    if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) {
		      throw new IndexOutOfBoundsException();
		    }
		    else if (len == 0) {
		      return;
		    }
		    int newcount = count + len;
		    if (newcount > buf.length) {
		      byte newbuf[] = new byte[Math.max(buf.length << 1, newcount)];
		      System.arraycopy(buf, 0, newbuf, 0, count);
		      buf = newbuf;
		    }
		    System.arraycopy(b, off, buf, count, len);
		    count = newcount;
		  }

		  /**
		   * Writes the complete contents of this byte array output stream to 
		   * the specified output stream argument, as if by calling the output 
		   * stream's write method using <code>out.write(buf, 0, count)</code>.
		   *
		   * @param      out   the output stream to which to write the data.
		   * @exception  IOException  if an I/O error occurs.
		   */
		  public synchronized void writeTo(OutputStream out) throws IOException {
		    out.write(buf, 0, count);
		  }

		  /**
		   * Resets the <code>count</code> field of this byte array output 
		   * stream to zero, so that all currently accumulated output in the 
		   * ouput stream is discarded. The output stream can be used again, 
		   * reusing the already allocated buffer space. 
		   *
		   * @see     java.io.ByteArrayInputStream#count
		   */
		  public synchronized void reset() {
		    count = 0;
		  }

		  /**
		   * Creates a newly allocated byte array. Its size is the current 
		   * size of this output stream and the valid contents of the buffer 
		   * have been copied into it. 
		   *
		   * @return  the current contents of this output stream, as a byte array.
		   * @see     java.io.ByteArrayOutputStream#size()
		   */
		  public synchronized byte toByteArray()[] {
		    byte newbuf[] = new byte[count];
		    System.arraycopy(buf, 0, newbuf, 0, count);
		    return newbuf;
		  }

		  /**
		   * Returns the current size of the buffer.
		   *
		   * @return  the value of the <code>count</code> field, which is the number
		   *          of valid bytes in this output stream.
		   * @see     java.io.ByteArrayOutputStream#count
		   */
		  public int size() {
		    return count;
		  }

		  /**
		   * Converts the buffer's contents into a string, translating bytes into
		   * characters according to the platform's default character encoding.
		   *
		   * @return String translated from the buffer's contents.
		   * @since   JDK1.1
		   */
		  @Override
		  public String toString() {
		    return new String(buf, 0, count);
		  }

		  /**
		   * Converts the buffer's contents into a string, translating bytes into
		   * characters according to the specified character encoding.
		   *
		   * @param   enc  a character-encoding name.
		   * @return String translated from the buffer's contents.
		   * @throws UnsupportedEncodingException
		   *         If the named encoding is not supported.
		   * @since   JDK1.1
		   */
		  public String toString(String enc) throws UnsupportedEncodingException {
		    return new String(buf, 0, count, enc);
		  }

		  /**
		   * Creates a newly allocated string. Its size is the current size of 
		   * the output stream and the valid contents of the buffer have been 
		   * copied into it. Each character <i>c</i> in the resulting string is 
		   * constructed from the corresponding element <i>b</i> in the byte 
		   * array such that:
		   * <blockquote><pre>
		   *     c == (char)(((hibyte &amp; 0xff) &lt;&lt; 8) | (b &amp; 0xff))
		   * </pre></blockquote>
		   *
		   * @deprecated This method does not properly convert bytes into characters.
		   * As of JDK&nbsp;1.1, the preferred way to do this is via the
		   * <code>toString(String enc)</code> method, which takes an encoding-name
		   * argument, or the <code>toString()</code> method, which uses the
		   * platform's default character encoding.
		   *
		   * @param      hibyte    the high byte of each resulting Unicode character.
		   * @return     the current contents of the output stream, as a string.
		   * @see        java.io.ByteArrayOutputStream#size()
		   * @see        java.io.ByteArrayOutputStream#toString(String)
		   * @see        java.io.ByteArrayOutputStream#toString()
		   */
		  @Deprecated
		  public String toString(int hibyte) {
		    return new String(buf, hibyte, 0, count);
		  }

		  /**
		   * Closing a <tt>ByteArrayOutputStream</tt> has no effect. The methods in
		   * this class can be called after the stream has been closed without
		   * generating an <tt>IOException</tt>.
		   * <p>
		   *
		   */
		  @Override
		  public void close() {
		  }

		  /**
		   * Returns the underlying byte buffer for this stream.
		   * 
		   * @return the underlying byte buffer for this stream
		   */
		  public synchronized byte[] getBuffer() {
		    return buf;
		  }

		  /**
		   * Returns a no-copy NSData of the byte buffer for this stream.
		   * 
		   * @return a no-copy NSData of the byte buffer for this stream
		   */
		  public synchronized NSData toNSData() {
		    return new NSData(buf, new NSRange(0, count), true);
		  }
		}
	}
}