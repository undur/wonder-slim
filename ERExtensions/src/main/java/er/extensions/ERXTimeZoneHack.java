package er.extensions;

import java.lang.reflect.Field;
import java.util.TimeZone;

import com.webobjects.foundation.NSTimeZone;

/**
 * Keeps NSTimeZone working on JDKs without the security manager's classes.
 *
 * NSTimeZone.systemTimeZone() reads the system time zone the first time it's asked, through
 * AccessController.doPrivileged( new sun.security.action.GetPropertyAction( "user.timezone" ) ), and keeps it in a
 * private field. GetPropertyAction is gone from the JDK, and AccessController is deprecated for removal. Their absence
 * is an Error, which systemTimeZone() doesn't catch, so the first use of the default time zone (formatting a timestamp,
 * for example) fails.
 *
 * This sets that field before anything asks, with the zone systemTimeZone() would have found, so systemTimeZone()
 * returns it without reaching either class. It must run before NSTimeZone's default time zone is first used: in
 * ERXApplication.main(), once the configuration is composed (so a user.timezone set in the configuration applies, as it
 * did) and before WOApplication.main() (which formats a timestamp when WOOutputPath is set).
 *
 * NSTimeZone.resetSystemTimeZone() clears the field, and the next use of the default time zone would then fail. Nothing
 * in WebObjects calls it.
 *
 * Based on code from Markus Stoll, posted by him in the WOWODC26 channel on the wocommunity Rocket Chat. It replaces
 * our own sun.security.action.GetPropertyAction, which only stood in for the missing class.
 */

public class ERXTimeZoneHack {

	/**
	 * Sets NSTimeZone's system time zone to the one it would itself have found: user.timezone, failing that the JVM's
	 * default time zone, failing that GMT.
	 *
	 * @throws IllegalStateException if NSTimeZone doesn't have the field this sets
	 */
	public static void enable() {
		final NSTimeZone systemTimeZone = systemTimeZone();

		try {
			final Field field = NSTimeZone.class.getDeclaredField( "__systemTimeZone" );
			field.setAccessible( true );
			field.set( null, systemTimeZone );
		}
		catch( NoSuchFieldException | IllegalAccessException e ) {
			throw new IllegalStateException( "Couldn't set NSTimeZone's system time zone. Without it, NSTimeZone fails on first use of the default time zone on this JDK", e );
		}
	}

	/**
	 * @return The time zone NSTimeZone.systemTimeZone() would find
	 */
	private static NSTimeZone systemTimeZone() {
		String name = System.getProperty( "user.timezone" );

		if( name == null || name.isBlank() ) {
			name = TimeZone.getDefault().getID();
		}

		NSTimeZone zone = null;

		if( name != null && !name.isBlank() ) {
			zone = NSTimeZone.timeZoneWithName( name.strip(), true );
		}

		return zone != null ? zone : NSTimeZone.getGMT();
	}
}