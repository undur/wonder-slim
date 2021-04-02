package er.extensions.foundation;

import java.util.Calendar;
import java.util.GregorianCalendar;

import com.webobjects.foundation.NSTimestamp;

public class ERXTimestampUtilities {

    private static GregorianCalendar calendarForTimestamp(NSTimestamp t) {
        GregorianCalendar calendar = (GregorianCalendar) Calendar.getInstance();
        calendar.setTime(t);
        return calendar;
    }

    public static int hourOfDay(NSTimestamp t) {
        return calendarForTimestamp(t).get(Calendar.HOUR_OF_DAY);
    }

    public static int minuteOfHour(NSTimestamp t) {
        return calendarForTimestamp(t).get(Calendar.MINUTE);
    }
}