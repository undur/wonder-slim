package er.extensions.foundation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSComparator;
import com.webobjects.foundation.NSMutableArray;

/**
 * Collection of {@link com.webobjects.foundation.NSArray NSArray} utilities.
 */
public class ERXArrayUtilities {

	/**
	 * Subtracts a single object from an array.
	 * 
	 * @param <T>
	 *            class of array items
	 * @param array
	 *            array to have value removed from it
	 * @param object
	 *            to be removed
	 * @return array after performing subtraction
	 */
	public static <T> NSArray<T> arrayMinusObject(Collection<T> array, T object) {
		if (object == null) {
			return new NSArray<>(array);
		}
		NSMutableArray<T> result = new NSMutableArray<>(array);
		boolean removed = true;
		while (removed) {
			removed = result.remove(object);
		}
		return result.immutableClone();
	}

	/**
	 * Intersects the elements of two arrays. This has the effect of stripping
	 * out duplicates.
	 * 
	 * @param <T>
	 *            class of array items
	 * @param array1
	 *            the first array
	 * @param array2
	 *            the second array
	 * @return the intersecting elements
	 */
	public static <T> NSArray<T> intersectingElements(Collection<? extends T> array1, Collection<? extends T> array2) {
		if (array1 == null || array1.isEmpty() || array2 == null || array2.isEmpty()) {
			return NSArray.emptyArray();
		}
		Collection<? extends T> smaller, larger;
		if (array1.size() > array2.size()) {
			smaller = array2;
			larger = array1;
		}
		else {
			smaller = array1;
			larger = array2;
		}
		Set<T> set = new HashSet<>(smaller);
		NSMutableArray<T> intersectingElements = new NSMutableArray<>();

		for (T object : larger) {
			if (set.contains(object)) {
				intersectingElements.add(object);
				set.remove(object);
			}
		}

		return !intersectingElements.isEmpty() ? intersectingElements : NSArray.emptyArray();
	}

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