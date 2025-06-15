package er.extensions.appserver;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import er.extensions.foundation.ERXMutableURL;
import er.extensions.foundation.ERXProperties;

public class ERXDevelopmentInstanceStopper {

	private static final Logger logger = LoggerFactory.getLogger(ERXDevelopmentInstanceStopper.class);

	/**
	 * Terminates a different instance of the same application that may already be running, only in dev mode.
	 * Set the property "er.extensions.ERXApplication.allowMultipleDevInstances" to "true" if you need to run multiple dev instances.
	 * 
	 * @return true if a previously running instance was stopped.
	 */
	public static boolean stopPreviousDevInstance() {

		if( !ERXApplication.isDevelopmentModeSafe() ) {
			return false;
		}

		if (ERXProperties.booleanForKeyWithDefault("er.extensions.ERXApplication.allowMultipleDevInstances", false)) {
			return false;
		}

		if (!ERXApplication.application().wasMainInvoked()) {
			return false;
		}

		try {
			final int port = ERXApplication.application().port().intValue();

			final boolean isNGApplication = isNGApplicationRunningOnPort(port);
			
			final URL url;

			if( isNGApplication ) {
				logger.info("Detected NG application already running on port " + port);
				url = urlForKillingNGApplicationOnPort(port);
			}
			else {
				logger.info("Assuming WO application already running on port " + port);
				url = urlForKillingWOApplicationOnPort(port);
			}

			logger.info("Attempting to stop previously running instance on port %s using URL %s".formatted(port,url) );

			url.openConnection().getContent();

			// Give the murder victim a little time to go to sleep
			// FIXME: We should be querying the application to see if shutdown is complete, instead of waiting for a fixed period // Hugi 2024-12-18
			Thread.sleep(2000);

			return true;
		}
		catch (Throwable e) {
			return true;
		}
	}

	/**
	 * @return The URL we can invoke to kill a WO application running on the given port
	 */
	private static URL urlForKillingWOApplicationOnPort( int port ) throws MalformedURLException {
		ERXMutableURL adapterUrl = new ERXMutableURL(ERXApplication.application().cgiAdaptorURL());

		if (ERXApplication.application().host() == null) {
			adapterUrl.setHost("localhost");
		}

		adapterUrl.appendPath(ERXApplication.application().name() + ERXApplication.application().applicationExtension());

		if (ERXApplication.application().isDirectConnectEnabled()) {
			adapterUrl.setPort(port);
		}
		else {
			adapterUrl.appendPath("-" + port);
		}

		adapterUrl.appendPath(ERXApplication.application().directActionRequestHandlerKey() + "/stop");

		return adapterUrl.toURL();
	}
	
	/**
	 * @return The URL we can invoke to kill an NG application running on the given port
	 */
	private static URL urlForKillingNGApplicationOnPort( int port ) throws MalformedURLException, URISyntaxException {
		return new URI( "http://localhost:%s/ng/dev/terminate".formatted(port) ).toURL();
	}

	/**
	 * @return true if the application running on the given port number is an ng-objects application
	 */
	private static boolean isNGApplicationRunningOnPort( int portNumber ) {
		final String urlString = String.format( "http://localhost:%s/ng/dev/type", 1200 );

		try( InputStream is = new URI( urlString ).toURL().openStream()) {
			final String type = new String( is.readAllBytes() );
			return "ng".equals( type );
		}
		catch( Throwable e ) {
			return false;
		}
	}
}