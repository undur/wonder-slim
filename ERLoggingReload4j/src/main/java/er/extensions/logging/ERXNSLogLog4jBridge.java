//
//  ERXNSLogLog4jBridge.java
//
//  Created and contributed by David Teran on Mon Oct 21 2002.
//
package er.extensions.logging;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.webobjects.foundation.NSLog;

import er.extensions.foundation.ERXProperties;

public class ERXNSLogLog4jBridge extends NSLog.PrintStreamLogger {

	public static final Logger log = Logger.getLogger( "NSLog" );
	public static final int OUT = 1;
	public static final int ERR = 2;
	public static final int DEBUG = 3;
	private final int type;

	public ERXNSLogLog4jBridge( int type ) {
		this.type = type;
	}

	/**
	 * If true, NSLog's settings will not affect log4j and the log4j.logger.NSLog setting will be used instead.
	 */
	private static boolean ignoreNSLogSettings() {
		return ERXProperties.booleanForKeyWithDefault( "er.extensions.ERXNSLogLog4jBridge.ignoreNSLogSettings", false );
	}

	@Override
	public void appendln( Object obj ) {
		if( isEnabled() ) {
			if( obj == null ) {
				obj = "";
			}
			switch( type ) {
			case OUT:
				log.info( obj.toString() );
				break;
			case ERR:
				log.warn( obj.toString() );
				break;
			case DEBUG:
				log.debug( obj.toString() );
				break;
			}
		}
		else {
			if( type == ERR ) {
				log.warn( obj != null ? obj.toString() : "" );
			}
		}
	}

	@Override
	public void setIsEnabled( boolean enabled ) {
		super.setIsEnabled( enabled );

		if( type == DEBUG && !ignoreNSLogSettings() ) {
			log.setLevel( enabled ? Level.DEBUG : Level.INFO );
		}
	}

	@Override
	public void setAllowedDebugLevel( int debugLevel ) {
		super.setAllowedDebugLevel( debugLevel );

		if( type == DEBUG && !ignoreNSLogSettings() ) {
			log.setLevel( debugLevel != NSLog.DebugLevelOff ? Level.DEBUG : Level.INFO );
		}
	}

	@Override
	public void appendln() {
		appendln( "" ); // Assuming people will always put "%n" at the end of the layout pattern.
	}

	@Override
	public void flush() {}
}