package er.extensions.foundation;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSComparator;

/**
 * Collection of {@link com.webobjects.foundation.NSArray NSArray} utilities.
 */
public class ERXArrayUtilities {

	/**
	 * Just like the method
	 * {@link com.webobjects.foundation.NSArray#sortedArrayUsingComparator(NSComparator)},
	 * except it catches the NSComparator.ComparisonException and, if thrown, it
	 * wraps it in a runtime exception. Returns null when passed null for array.
	 * 
	 * @param <T>
	 *            class of array items
	 * @param array
	 *            the array to sort
	 * @param comparator
	 *            the comparator
	 * @return the sorted array
	 */
	public static <T> NSArray<T> sortedArrayUsingComparator(NSArray<T> array, NSComparator comparator) {
		if (array == null || array.size() < 2) {
			return array;
		}
		NSArray<T> result = array;
		try {
			result = array.sortedArrayUsingComparator(comparator);
		}
		catch (NSComparator.ComparisonException e) {
			throw new RuntimeException(e);
		}

		return result;
	}
}