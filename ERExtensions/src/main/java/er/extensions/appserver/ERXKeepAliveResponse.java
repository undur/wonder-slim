package er.extensions.appserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSForwardException;

/**
 * Special response that keeps the connection alive and pushes the data to the client.
 * It does this by opening a stream that has small buffer but huge length.
 * 
 * @author ak
 */

public class ERXKeepAliveResponse extends WOResponse {

	private static final Logger log = LoggerFactory.getLogger(ERXKeepAliveResponse.class);

	/**
	 * Queue to push the items into.
	 */
	protected Queue<byte[]> _queue = new ConcurrentLinkedQueue<>();

	/**
	 * Current data to write to client.
	 */
	protected byte[] _current = null;

	/**
	 * Current index in
	 */
	protected int _currentIndex = 0;

	/**
	 * Set by {@link #reset()}: the stream ends once it's read.
	 */
	private boolean _ended = false;

	public ERXKeepAliveResponse() {
		// A buffer size of one byte, so each byte is passed on as soon as it's read rather than when a buffer fills, and
		// a content length the client will keep reading for (the last two arguments).
		setContentStream(new InputStream() {
			@Override
			public int read() throws IOException {
				synchronized (_queue) {
					if (_current != null && _currentIndex >= _current.length) {
						_current = null;
						_currentIndex = 0;
					}
					// Wait only while there's nothing queued: data pushed while the previous item was being written is
					// already waiting, and wait() can also return without a notify.
					while (_current == null && !_ended) {
						_current = _queue.poll();

						if (_current == null) {
							try {
								log.debug("waiting: {}", _queue.hashCode());
								_queue.wait();
							}
							catch (InterruptedException e) {
								return -1;
							}
						}
					}
					if (_ended) {
						return -1;
					}
					log.debug("writing: {}", _currentIndex);
					return _current[_currentIndex++] & 0xFF;
				}
			}

		}, 1, Long.MAX_VALUE); // MS: turning it up to 11
	}

	/**
	 * Enqueues the data for this string using the response encoding.
	 * 
	 * @param str the string to push
	 */
	public void push(String str) {
		try {
			push(str.getBytes(contentEncoding()));
		}
		catch (UnsupportedEncodingException e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
	}
	
	/**
	 * Enqueues the data.
	 */
	public void push(byte[] data) {
		if (log.isDebugEnabled()) {
			log.debug("pushing: " + _queue.hashCode());
		}
		synchronized (_queue) {
			_queue.offer(data);
			_queue.notify();
		}
	}

	/**
	 * Ends the response: data not yet written is dropped, and the stream ends.
	 */
	public void reset() {
		synchronized (_queue) {
			_current = null;
			_currentIndex = 0;
			_queue.clear();
			_ended = true;
			_queue.notify();
		}
	}
}