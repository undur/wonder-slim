package er.extensions.eof;

/**
 * FIXME: Everything here is stupid.
 * 
 * This class was introduced temporarily to serve as container for the public variables previously used from here, until they get replaced on-site. 
 */

public class ERXConstant {

	public static final Class[] NotificationClassArray = { com.webobjects.foundation.NSNotification.class };
    public static final Integer OneInteger = 1;
    public static final Integer ZeroInteger = 0;
    public static final Class[] StringClassArray = new Class[] { String.class };
    public static final Object[] EmptyObjectArray = new Object[] {};

    public static final int MAX_INT=2500;

    protected static Integer[] INTEGERS=new Integer[MAX_INT];

    static {
        for (int i=0; i<MAX_INT; i++) INTEGERS[i]=Integer.valueOf(i);
    }

    /**
     * Returns an Integer for a given int
     * @return potentially cache Integer for a given int
     */
    public static Integer integerForInt(int i) {
        return (i>=0 && i<MAX_INT) ? INTEGERS[i] : Integer.valueOf(i);
    }
}