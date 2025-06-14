package er.extensions.components._private;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WODynamicElementCreationException;
import com.webobjects.appserver._private.WODynamicGroup;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSKeyValueCodingAdditions;

import er.extensions.appserver.ERXWOContext;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXValueUtilities;

/**
 * Replacement for WORepetition. It is installed via ERXPatcher.setClassForName(ERXWORepetition.class, "WORepetition") 
 * into the runtime system, so you don't need to reference it explicitly.
 * <ul>
 * <li>adds support for {@link java.util.List} and arrays (e.g. String[]), in addition to
 * {@link com.webobjects.foundation.NSArray} and {@link java.util.Vector} (which is a {@link java.util.List} in 1.4). This
 * is listed as Radar #3325342 since June 2003.</li>
 * <li>help with backtracking issues by adding not only the current index, but also the current object's hash code to
 * the element id, so it looks like "x.y.12345.z".<br>
 * If they don't match when invokeAction is called, the list is searched for a matching object. If none is found, then:
 * <ul>
 * <li>if the property <code>er.extensions.ERXWORepetition.raiseOnUnmatchedObject=true</code> -
 * an {@link ERXWORepetition.UnmatchedObjectException} is thrown</li>
 * <li>if <code>notFoundMarker</code> is bound, that is used for the item in the repetition.  This can be used to flag
 * special handling in the action method, possibly useful for Ajax requests</li>
 * <li>otherwise, the action is ignored</li>
 * </ul>
 * This feature is turned on globally if <code>er.extensions.ERXWORepetition.checkHashCodes=true</code> or on a
 * per-component basis by setting the <code>checkHashCodes</code> binding to true or false.<br>
 * <em>Known issues:</em>
 * <ul>
 * <li>you can't re-generate your list by creating new objects between the appendToReponse and the next
 * takeValuesFromRequest unless you use <code>uniqueKey</code> and the value for that key is consistent across
 * the object instances<br>
 * When doing this by fetching EOs, this is should not a be problem, as the EO most probably has the same hashCode if
 * the EC stays the same. </li>
 * <li>Your moved object should still be in the list.</li>
 * <li>Form values are currently not fixed, which may lead to NullpointerExceptions or other failures. However, if they
 * happen, by default you would have used the wrong values, so it may be arguable that having an error is better...</li>
 * </ul>
 * </li>
 * </ul>
 * Note that this implementation adds a small amount of overhead due to the creation of the Context for each RR phase,
 * but this is preferable to having to give so many parameters.
 * 
 * As an alternative to the default use of System.identityHashCode to unique your items, you can set the binding "uniqueKey" 
 * to be a string keypath on your items that can return a unique key for the item.  For instance, if you are using 
 * ERXGenericRecord, you can set uniqueKey = "rawPrimaryKey"; if your EO has an integer primary key, and this will make
 * the uniquing value be the primary key instead of the hash code.  While this reveals the primary keys of your items,
 * the set of possible valid matches is still restricted to only those that were in the list to begin with, so no 
 * additional capabilities are available to users.  <code>uniqueKey</code> does <b>not</b> have to return an integer.
 * 
 * @binding list the array or list of items to iterate over
 * @binding item the current item in the iteration
 * @binding count the total number of items to iterate over
 * @binding index the current index in the iteration
 * @binding uniqueKey a String keypath on item (relative to item, not relative to the component) returning a value whose toString() is unique for this component
 * @binding checkHashCodes if true, checks the validity of repetition references during the RR loop
 * @binding raiseOnUnmatchedObject if true, an exception is thrown when the repetition does not find a matching object
 * @binding debugHashCodes if true, prints out hashcodes for each entry in the repetition as it is traversed
 * @binding batchFetch a comma-separated list of keypaths on the "list" array binding to batch fetch
 * @binding eoSupport try to use globalIDs to determine the hashCode for EOs
 * @binding notFoundMarker used for the item in the repetition if checkHashCodes is true, don't bind directly to null as that will be translated to false
 * 
 * @property er.extensions.ERXWORepetition.checkHashCodes add hash codes to element IDs so backtracking can be controlled
 * @property er.extensions.ERXWORepetition.raiseOnUnmatchedObject if an object wasn't found, raise an exception (if unset, the wrong object is used)
 * @property er.extensions.ERXWORepetition.eoSupport use hash code of GlobalID instead of object's hash code if it is an EO
 * 
 * @author ak
 */

public class ERXWORepetition extends WODynamicGroup {

	private static final Logger log = LoggerFactory.getLogger(ERXWORepetition.class);

	private WOAssociation _list;
	private WOAssociation _item;
	private WOAssociation _count;
	private WOAssociation _index;
	private WOAssociation _uniqueKey;
	private WOAssociation _checkHashCodes;
	private WOAssociation _raiseOnUnmatchedObject;
	private WOAssociation _eoSupport;
	private WOAssociation _debugHashCodes;
	private WOAssociation _batchFetch;
	private WOAssociation _notFoundMarker;

	private static boolean _checkHashCodesDefault = ERXProperties.booleanForKeyWithDefault("er.extensions.ERXWORepetition.checkHashCodes", ERXProperties.booleanForKey(ERXWORepetition.class.getName() + ".checkHashCodes"));
	private static boolean _raiseOnUnmatchedObjectDefault = ERXProperties.booleanForKeyWithDefault("er.extensions.ERXWORepetition.raiseOnUnmatchedObject", ERXProperties.booleanForKey(ERXWORepetition.class.getName() + ".raiseOnUnmatchedObject"));
	
	private static class UnmatchedObjectException extends RuntimeException {}

	/**
	 * WOElements must be reentrant, so we need a context object or will have to add the parameters to every method.
	 * Note that it's OK to have no object at all (a.k.a. null)
	 * 
	 * FIXME: Do we _actually_ want null safety here? // Hugi 2025-06-14
	 */
	private static class Context {

		private NSArray<Object> nsarray;
		private List<Object> list;
		private Object[] array;

		private Context(Object object) {
			if (object != null) {
				if (object instanceof NSArray) {
					nsarray = (NSArray<Object>) object;
				}
				else if (object instanceof List) {
					list = (List<Object>) object;
				}
				else if (object instanceof Object[]) {
					array = (Object[]) object;
				}
				else {
					throw new IllegalArgumentException("Evaluating 'list' binding returned a " + object.getClass().getName() +
							" when it should return either a NSArray, an Object[] array or a java.util.List .");
				}
			}
		}

		/**
		 * @return Size of the contained list 
		 */
		private int count() {
			if (nsarray != null) {
				return nsarray.count();
			}
			else if (list != null) {
				return list.size();
			}
			else if (array != null) {
				return array.length;
			}
			return 0;
		}

		/**
		 * @return object at index the index
		 */
		private Object objectAtIndex(int i) {
			if (nsarray != null) {
				return nsarray.objectAtIndex(i);
			}
			else if (list != null) {
				return list.get(i);
			}
			else if (array != null) {
				return array[i];
			}
			return null;
		}
	}

	public ERXWORepetition(String string, NSDictionary<String, WOAssociation> associations, WOElement woelement) {
		super(null, null, woelement);

		_list = associations.objectForKey("list");
		_item = associations.objectForKey("item");
		_count = associations.objectForKey("count");
		_index = associations.objectForKey("index");
		_uniqueKey = associations.objectForKey("uniqueKey");
		_checkHashCodes = associations.objectForKey("checkHashCodes");
		_raiseOnUnmatchedObject = associations.objectForKey("raiseOnUnmatchedObject");
		_debugHashCodes = associations.objectForKey("debugHashCodes");
		_eoSupport = associations.objectForKey("eoSupport");
		_batchFetch = associations.objectForKey("batchFetch");
		_notFoundMarker = associations.objectForKey("notFoundMarker");
		
		if (_list == null && _count == null) {
			_failCreation("Missing 'list' or 'count' attribute.");
		}
		if (_list != null && _item == null) {
			_failCreation("Missing 'item' attribute with 'list' attribute.");
		}
		if (_list != null && _count != null) {
			_failCreation("Illegal use of 'count' attribute with 'list' attribute.");
		}
		if (_count != null && (_list != null || _item != null)) {
			_failCreation("Illegal use of 'list', or 'item'attributes with 'count' attribute.");
		}
		if (_item != null && !_item.isValueSettable()) {
			_failCreation("Illegal read-only 'item' attribute.");
		}
		if (_index != null && !_index.isValueSettable()) {
			_failCreation("Illegal read-only 'index' attribute.");
		}
	}

	/**
	 * Utility to throw an exception if the bindings are incomplete.
	 */
	private void _failCreation(String message) {
		throw new WODynamicElementCreationException("<" + getClass().getName() + "> " + message);
	}

	private int hashCodeForObject(WOComponent component, Object object) {

		int hashCode;

		if (object == null) {
			hashCode = 0;
		}

		hashCode = System.identityHashCode(object);

		// @see java.lang.Math#abs for an explanation of this
		if (hashCode == Integer.MIN_VALUE) {
			hashCode = 37; // MS: random prime number
		}

		hashCode = Math.abs(hashCode);

		if (_debugHashCodes != null && _debugHashCodes.booleanValueInComponent(component)) {
			log.info("debugHashCodes for '{}', {} = {}", _list.keyPath(), object, hashCode);
		}

		return hashCode;
	}
	
	private String keyForObject(WOComponent component, Object object) {
		String uniqueKeyPath = (String)_uniqueKey.valueInComponent(component);
		Object uniqueKey = NSKeyValueCodingAdditions.Utility.valueForKeyPath(object, uniqueKeyPath);
		if (uniqueKey == null) {
			throw new IllegalArgumentException("Can't use null as uniqueKey for " + object);
		}
		
		String key = ERXWOContext.safeIdentifierName(uniqueKey.toString());

		if (_debugHashCodes != null && _debugHashCodes.booleanValueInComponent(component)) {
			log.info("debugHashCodes for '{}', {} = {}", _list.keyPath(), object, key);
		}
		return key;
	}

	/**
	 * Prepares the WOContext for the loop iteration.
	 */
	private void _prepareForIterationWithIndex(Context context, int index, WOContext wocontext, WOComponent wocomponent, boolean checkHashCodes) {
		Object object = null;

		if (_item != null) {
			object = context.objectAtIndex(index);
			_item._setValueNoValidation(object, wocomponent);
		}

		if (_index != null) {
			_index._setValueNoValidation(index, wocomponent);
		}

		boolean didAppend = false;

		if (checkHashCodes) {
			if (object != null) {
				String elementID = null;
				if (_uniqueKey == null) {
					int hashCode = hashCodeForObject(wocomponent, object);
					if (hashCode != 0) {
						elementID = String.valueOf(hashCode);
					}
				}
				else {
					elementID = keyForObject(wocomponent, object);
				}

				if (elementID != null) {
					if (index != 0) {
						wocontext.deleteLastElementIDComponent();
					}
					log.debug("prepare {}->{}", elementID, object);
					wocontext.appendElementIDComponent(elementID);
					didAppend = true;
				}
			}
		}

		if (!didAppend) {
			if (index != 0) {
				wocontext.incrementLastElementIDComponent();
			}
			else {
				wocontext.appendZeroElementIDComponent();
			}
		}
	}

	/**
	 * Cleans the WOContext after the loop iteration.
	 */
	private void _cleanupAfterIteration(int i, WOContext wocontext, WOComponent wocomponent) {
		if (_item != null) {
			_item._setValueNoValidation(null, wocomponent);
		}
		if (_index != null) {
			_index._setValueNoValidation(i, wocomponent);
		}
		wocontext.deleteLastElementIDComponent();
	}

	/**
	 * Fills the context with the object given in the "list" binding.
	 */
	private String _indexStringForSenderAndElement(String senderID, String elementID) {
		int dotOffset = elementID.length() + 1;
		int nextDotOffset = senderID.indexOf('.', dotOffset);
		String indexString;
		if (nextDotOffset < 0) {
			indexString = senderID.substring(dotOffset);
		}
		else {
			indexString = senderID.substring(dotOffset, nextDotOffset);
		}
		return indexString;
	}

	private String _indexOfChosenItem(WORequest worequest, WOContext wocontext) {
		String indexString = null;
		String senderID = wocontext.senderID();
		String elementID = wocontext.elementID();
		if (senderID.startsWith(elementID)) {
			int i = elementID.length();
			if (senderID.length() > i && senderID.charAt(i) == '.')
				indexString = _indexStringForSenderAndElement(senderID, elementID);
		}
		return indexString;
	}

	private int _count(Context context, WOComponent wocomponent) {
		int count;
		if (_list != null) {
			count = context.count();
		}
		else {
			Object object = _count.valueInComponent(wocomponent);
			if (object != null) {
				count = ERXValueUtilities.intValue(object);
			}
			else {
				log.error("{} 'count' evaluated to null in component {}.\nRepetition  count reset to 0.", this, wocomponent);
				count = 0;
			}
		}
		return count;
	}

	private Context createContext(WOComponent wocomponent) {
		Object list = (_list != null ? _list.valueInComponent(wocomponent) : null);
		/*
		if(list instanceof NSArray) {
			if (_batchFetch != null) {
				String batchFetchKeyPaths = (String)_batchFetch.valueInComponent(wocomponent);
				if (batchFetchKeyPaths != null) {
					NSArray<String> keyPaths = NSArray.componentsSeparatedByString(batchFetchKeyPaths, ",");
					if (keyPaths.count() > 0) {
						ERXBatchFetchUtilities.batchFetch((NSArray)list, keyPaths, true);
					}
				}
			}
			ERXDatabaseContextDelegate.setCurrentBatchObjects((NSArray)list);
		}
		*/
		return new Context(list);
	}

	@Override
	public void takeValuesFromRequest(WORequest worequest, WOContext wocontext) {
		WOComponent wocomponent = wocontext.component();
		Context context = createContext(wocomponent);

		int count = _count(context, wocomponent);
		boolean checkHashCodes = checkHashCodes(wocomponent);
		if (log.isDebugEnabled()) {
			log.debug("takeValuesFromRequest: {} - {}", wocontext.elementID(), wocontext.request().formValueKeys());
		}
		for (int index = 0; index < count; index++) {
			_prepareForIterationWithIndex(context, index, wocontext, wocomponent, checkHashCodes);
			super.takeValuesFromRequest(worequest, wocontext);
		}
		if (count > 0) {
			_cleanupAfterIteration(count, wocontext, wocomponent);
		}
	}

	@Override
	public WOActionResults invokeAction(WORequest worequest, WOContext wocontext) {
		WOComponent wocomponent = wocontext.component();
		Context repetitionContext = createContext(wocomponent);

		int count = _count(repetitionContext, wocomponent);

		WOActionResults woactionresults = null;
		String indexString = _indexOfChosenItem(worequest, wocontext);

		int index = 0;
		boolean checkHashCodes = checkHashCodes(wocomponent);

		if (indexString != null && ! checkHashCodes) {
			index = Integer.parseInt(indexString);
		}
		
		if (indexString != null) {
			if (_item != null) {
				Object object = null;
				if (checkHashCodes) {
					boolean found = false;
					
					if (_uniqueKey == null) {
						int hashCode = Integer.parseInt(indexString);
						int otherHashCode = 0;
						for (int i = 0; i < repetitionContext.count() && !found; i++) {
							Object o = repetitionContext.objectAtIndex(i);
							otherHashCode = hashCodeForObject(wocomponent, o);
							if (otherHashCode == hashCode) {
								object = o;
								index = i;
								found = true;
							}
						}
						if (found) {
							log.debug("Found object: {} vs {}", otherHashCode, hashCode);
						} else {
							log.warn("Wrong object: {} vs {} (array = {})", otherHashCode, hashCode, repetitionContext.nsarray);
						}
					}
					else {
						String key = indexString;
						String otherKey = null;
						for (int i = 0; i < repetitionContext.count() && !found; i++) {
							Object o = repetitionContext.objectAtIndex(i);
							otherKey = keyForObject(wocomponent, o);
							if (otherKey.equals(key)) {
								object = o;
								index = i;
								found = true;
							}
						}
						if (found) {
							log.debug("Found object: {} vs {}", otherKey, key);
						} else {
							log.warn("Wrong object: {} vs {} (array = {})", otherKey, key, repetitionContext.nsarray);
						}
					}

					if (!found) {
						if (raiseOnUnmatchedObject(wocomponent)) {
							throw new UnmatchedObjectException();
						}
						if (_notFoundMarker == null) {
							return wocontext.page();
						}
						object = _notFoundMarker.valueInComponent(wocomponent);
					}
				}
				else {
					if (index >= repetitionContext.count()) {
						if (raiseOnUnmatchedObject(wocomponent)) {
							throw new UnmatchedObjectException();
						}
						return wocontext.page();
					}
					object = repetitionContext.objectAtIndex(index);
				}
				_item._setValueNoValidation(object, wocomponent);
			}
			if (_index != null) {
				_index._setValueNoValidation(index, wocomponent);
			}
			wocontext.appendElementIDComponent(indexString);
			log.debug("invokeAction: {}", wocontext.elementID());
			woactionresults = super.invokeAction(worequest, wocontext);
			wocontext.deleteLastElementIDComponent();
		}
		else {
			for (int i = 0; i < count && woactionresults == null; i++) {
				_prepareForIterationWithIndex(repetitionContext, i, wocontext, wocomponent, checkHashCodes);
				woactionresults = super.invokeAction(worequest, wocontext);
			}
			if (count > 0) {
				_cleanupAfterIteration(count, wocontext, wocomponent);
			}
		}
		return woactionresults;
	}
	
	@Override
	public void appendToResponse(WOResponse woresponse, WOContext wocontext) {
		final WOComponent wocomponent = wocontext.component();
		final Context context = createContext(wocomponent);

		final int count = _count(context, wocomponent);
		final boolean checkHashCodes = checkHashCodes(wocomponent);
		log.debug("appendToResponse: {}", wocontext.elementID());

		for (int index = 0; index < count; index++) {
			_prepareForIterationWithIndex(context, index, wocontext, wocomponent, checkHashCodes);
			appendChildrenToResponse(woresponse, wocontext);
		}
		if (count > 0) {
			_cleanupAfterIteration(count, wocontext, wocomponent);
		}
	}

	private boolean checkHashCodes(WOComponent wocomponent) {

		if (_checkHashCodes != null) {
			return _checkHashCodes.booleanValueInComponent(wocomponent);
		}

		return _checkHashCodesDefault;
	}

	private boolean raiseOnUnmatchedObject(WOComponent wocomponent) {

		if (_raiseOnUnmatchedObject != null) {
			return _raiseOnUnmatchedObject.booleanValueInComponent(wocomponent);
		}

		return _raiseOnUnmatchedObjectDefault;
	}

	@Override
	public String toString() {
		return new StringBuilder().append('<').append(getClass().getName())
				.append(" list: ").append(_list)
				.append(" item: ").append(_item)
				.append(" count: ").append(_count)
				.append(" index: ").append(_index).append('>').toString();
	}
}