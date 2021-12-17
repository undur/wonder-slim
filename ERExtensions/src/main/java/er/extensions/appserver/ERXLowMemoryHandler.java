package er.extensions.appserver;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.foundation.NSLog;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;

import er.extensions.foundation.ERXProperties;

public class ERXLowMemoryHandler {

	private static final Logger log = LoggerFactory.getLogger(ERXLowMemoryHandler.class);

	/**
	 * Notification to get posted when we get an OutOfMemoryError or when memory
	 * passes the low memory threshold set in
	 * er.extensions.ERXApplication.memoryLowThreshold. You should register your
	 * caching classes for this notification so you can release memory.
	 * Registration should happen at launch time.
	 */
	public static final String LowMemoryNotification = "LowMemoryNotification";

	/**
	 * Notification to get posted when we have recovered from a LowMemory condition.
	 */
	public static final String LowMemoryResolvedNotification = "LowMemoryResolvedNotification";

	/**
	 * Notification to get posted when we are on the brink of running out of memory.
	 * By default, sessions will begin to be refused when this happens as well.
	 */
	public static final String StarvedMemoryNotification = "StarvedMemoryNotification";

	/**
	 * Notification to get posted when we have recovered from a StarvedMemory condition.
	 */
	public static final String StarvedMemoryResolvedNotification = "StarvedMemoryResolvedNotification";

	/**
	 * Buffer we reserve lowMemBufSize KB to release when we get an
	 * OutOfMemoryError, so we can post our notification and do other stuff
	 */
	private byte lowMemBuffer[];
	
	/**
	 * Size of the memory in KB to reserve for low-mem situations, pulled from the system property
	 * <code>er.extensions.ERXApplication.lowMemBufferSize</code>. Default is 0, indicating no reserve.
	 */
	private final int lowMemBufferSize;

	/**
	 * Time that garbage collection was last called when checking memory.
	 */
	private long _lastGC = 0;

	/**
	 * Holds the value of the property er.extensions.ERXApplication.memoryStarvedThreshold
	 */
	private BigDecimal _memoryStarvedThreshold;

	/**
	 * Holds the value of the property er.extensions.ERXApplication.memoryLowThreshold
	 */
	private BigDecimal _memoryLowThreshold;

	private boolean _isMemoryLow = false;
	private boolean _isMemoryStarved = false;

	public ERXLowMemoryHandler() {
		_memoryStarvedThreshold = ERXProperties.bigDecimalForKeyWithDefault("er.extensions.ERXApplication.memoryStarvedThreshold", _memoryStarvedThreshold);
		_memoryLowThreshold = ERXProperties.bigDecimalForKeyWithDefault("er.extensions.ERXApplication.memoryLowThreshold", _memoryLowThreshold);
		lowMemBufferSize = ERXProperties.intForKeyWithDefault("er.extensions.ERXApplication.lowMemBufferSize", 0);

		if (lowMemBufferSize > 0) {
			lowMemBuffer = new byte[lowMemBufferSize];
		}
	}

	/**
	 * Handles the potentially fatal OutOfMemoryError by quitting the
	 * application ASAP. Broken out into a separate method to make custom error
	 * handling easier, ie. generating your own error pages in production, etc.
	 * 
	 * @param throwable to check if it is a fatal exception.
	 * @return true if we should quit
	 */
	public boolean shouldQuit(Throwable throwable) {

		boolean shouldQuit = false;

		if (throwable instanceof Error) {
			if (throwable instanceof OutOfMemoryError) {
				boolean shouldExitOnOOMError = ERXProperties.booleanForKeyWithDefault("er.extensions.AppShouldExitOnOutOfMemoryError", true);
				shouldQuit = shouldExitOnOOMError;
				// AK: I'm not sure this actually works, in particular when the
				// buffer is in the long-running generational mem, but it's
				// worth a try.
				// what we do is set up a last-resort buffer during startup
				if (lowMemBuffer != null) {
					Runtime.getRuntime().freeMemory();
					try {
						lowMemBuffer = null;
						System.gc();
						log.error("Ran out of memory, sending notification to clear caches");
						log.error("Ran out of memory, sending notification to clear caches", throwable);
						NSNotificationCenter.defaultCenter().postNotification(new NSNotification(LowMemoryNotification, this));
						shouldQuit = false;
						// try to reclaim our twice of our buffer
						// if this worked maybe we can continue running
						lowMemBuffer = new byte[lowMemBufferSize * 2];
						// shrink buffer to normal size
						lowMemBuffer = new byte[lowMemBufferSize];
					}
					catch (Throwable ex) {
						shouldQuit = shouldExitOnOOMError;
					}
				}
				// We first log just in case the log4j call puts us in a bad state.
				if (shouldQuit) {
					NSLog.err.appendln("Ran out of memory, killing this instance");
					log.error("Ran out of memory, killing this instance");
					log.error("Ran out of memory, killing this instance", throwable);
				}
			}
			else {
				// We log just in case the log4j call puts us in a bad state.
				NSLog.err.appendln("java.lang.Error \"" + throwable.getClass().getName() + "\" occured.");
				log.error("java.lang.Error \"" + throwable.getClass().getName() + "\" occured.", throwable);
			}
			
		}

		return shouldQuit;
	}

	/**
	 * <p>
	 * Checks if the free memory is less than the threshold given in
	 * <code>er.extensions.ERXApplication.memoryStarvedThreshold</code> (should
	 * be set to around 0.90 meaning 90% of total memory or 100 meaning 100 MB
	 * of minimal available memory) and if it is greater start to refuse new
	 * sessions until more memory becomes available. This helps when the
	 * application is becoming unresponsive because it's more busy garbage
	 * collecting than processing requests. The default is to do nothing unless
	 * the property is set. This method is called on each request, but garbage
	 * collection will be done only every minute.
	 * </p>
	 * 
	 * <p>
	 * Additionally, you can set
	 * <code>er.extensions.ERXApplication.memoryLowThreshold</code>, which you
	 * can set at a higher "warning" level, before the situation is critical.
	 * </p>
	 * 
	 * <p>
	 * Both of these methods post notifications both at the start of the event
	 * as well as the end of the event
	 * (LowMemoryNotification/LowMemoryResolvedNotification and
	 * StarvedMemoryNotification and StarvedMemoryResolvedNotification).
	 * </p>
	 * 
	 * @author ak
	 */
	public void checkMemory() {
		boolean memoryLow = checkMemory(_memoryLowThreshold, false);

		if (memoryLow != _isMemoryLow) {
			if (!memoryLow) {
				log.warn("App is no longer low on memory");
				NSNotificationCenter.defaultCenter().postNotification(new NSNotification(LowMemoryResolvedNotification, this));
			}
			else {
				log.error("App is low on memory");
				NSNotificationCenter.defaultCenter().postNotification(new NSNotification(LowMemoryNotification, this));
			}
			_isMemoryLow = memoryLow;
		}

		boolean memoryStarved = checkMemory(_memoryStarvedThreshold, true);

		if (memoryStarved != _isMemoryStarved) {
			if (!memoryStarved) {
				log.warn("App is no longer starved, handling new sessions again");
				NSNotificationCenter.defaultCenter().postNotification(new NSNotification(StarvedMemoryResolvedNotification, this));
			}
			else {
				log.error("App is starved, starting to refuse new sessions");
				NSNotificationCenter.defaultCenter().postNotification(new NSNotification(StarvedMemoryNotification, this));
			}
			_isMemoryStarved = memoryStarved;
		}
	}
	
	public boolean isMemoryStarved() {
		return _isMemoryStarved;
	}

	protected boolean checkMemory(BigDecimal memoryThreshold, boolean attemptGC) {
		boolean pastThreshold = false;
		if (memoryThreshold != null) {
			long max = Runtime.getRuntime().maxMemory();
			long total = Runtime.getRuntime().totalMemory();
			long free = Runtime.getRuntime().freeMemory() + (max - total);
			long used = max - free;
			long starvedThreshold = (long) (memoryThreshold.doubleValue() < 1.0 ? memoryThreshold.doubleValue() * max : (max - (memoryThreshold.doubleValue() * 1024 * 1024)));

			synchronized (this) {
				long time = System.currentTimeMillis();
				if (attemptGC && (used > starvedThreshold) && (time > _lastGC + 60 * 1000L)) {
					_lastGC = time;
					Runtime.getRuntime().gc();
					max = Runtime.getRuntime().maxMemory();
					total = Runtime.getRuntime().totalMemory();
					free = Runtime.getRuntime().freeMemory() + (max - total);
					used = max - free;
				}
				pastThreshold = (used > starvedThreshold);
			}
		}
		return pastThreshold;
	}
}