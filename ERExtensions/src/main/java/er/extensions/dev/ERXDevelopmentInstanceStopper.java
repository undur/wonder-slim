package er.extensions.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import er.extensions.appserver.ERXApplication;
import ng.dev.NGDevInstanceStopper;

/**
 * Takes the port from the application holding it, when a development instance can't bind to it: whatever ng-objects or
 * WebObjects application holds the port is asked to stop, and killed if it doesn't. The work is shared with ng-objects
 * ({@link NGDevInstanceStopper}); this decides when to do it and logs what happened.
 */
public class ERXDevelopmentInstanceStopper {

	private static final Logger logger = LoggerFactory.getLogger( ERXDevelopmentInstanceStopper.class );

	/**
	 * Stops the application holding this application's port, in development mode only.
	 *
	 * @return true if the port is free now
	 */
	public static boolean stopPreviousDevInstance() {

		if( !ERXApplication.isDevelopmentModeSafe() ) {
			return false;
		}

		final int port = ERXApplication.application().port().intValue();
		final NGDevInstanceStopper.Result result = NGDevInstanceStopper.stopInstanceOnPort( port );

		if( result.released() ) {
			logger.info( result.describe( port ) );
		}
		else {
			logger.warn( result.describe( port ) );
		}

		return result.released();
	}
}
