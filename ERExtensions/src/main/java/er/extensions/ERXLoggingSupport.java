package er.extensions;

public interface ERXLoggingSupport {

	/**
	 * Temporary facade while we move logging out of ERExtensions.
	 * Do some uglylogging while we discover where this method is used. 
	 */
	public static void configureLoggingWithSystemProperties() {
		System.out.println("!!DEBUG!! configureLoggingWithSystemProperties invoked");
		ERXLoggingSupport.runLoggingBridgeMethod( "configureLoggingWithSystemProperties" );
	}

	/**
	 * Temporary facade while we move logging out of ERExtensions.
	 * Do some uglylogging while we discover where this method is used. 
	 */
	public static void reInitConsoleAppenders() {
		System.out.println("!!DEBUG!! reInitConsoleAppenders invoked");
		ERXLoggingSupport.runLoggingBridgeMethod( "reInitConsoleAppenders" );
	
	}

	private static void runLoggingBridgeMethod( final String methodName ) {
		try {
			Class<?> bridge = Class.forName("er.extensions.logging.ERXTemporaryLoggingBridge");
			bridge.getMethod(methodName, null).invoke(null, null);
		}
		catch (Exception e) {
			System.out.println("Failed to locate the logging bridge or run a method on it. WARNING! SILENT RUNNING!" );
			e.printStackTrace();
		}
	}

}