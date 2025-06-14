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

	private final WOAssociation _list;
	private final WOAssociation _item;
	private final WOAssociation _count;
	private final WOAssociation _index;
	private final WOAssociation _uniqueKey;
	private final WOAssociation _checkHashCodes;
	private final WOAssociation _raiseOnUnmatchedObject;
	private final WOAssociation _debugHashCodes;
	private final WOAssociation _notFoundMarker;

	private final WOAssociation _eoSupport;
	private final WOAssociation _batchFetch;

	private static final boolean _checkHashCodesDefault = ERXProperties.booleanForKeyWithDefault("er.extensions.ERXWORepetition.checkHashCodes", ERXProperties.booleanForKey(ERXWORepetition.class.getName() + ".checkHashCodes"));
	private static final boolean _raiseOnUnmatchedObjectDefault = ERXProperties.booleanForKeyWithDefault("er.extensions.ERXWORepetition.raiseOnUnmatchedObject", ERXProperties.booleanForKey(ERXWORepetition.class.getName() + ".raiseOnUnmatchedObject"));
	
	private static class UnmatchedObjectException extends RuntimeException {}

	/**
	 * Wraps the list passed to us 
	 * 
	 * FIXME: Do we _actually_ want null safety here? // Hugi 2025-06-14
	 */
	private static class ListWrapper {

		private List<Object> list;
		private Object[] array;

		private ListWrapper(Object wrapped) {
			if (wrapped != null) {
				if (wrapped instanceof List) {
					list = (List<Object>) wrapped;
				}
				else if (wrapped instanceof Object[]) {
					array = (Object[]) wrapped;
				}
				else {
					throw new IllegalArgumentException("Evaluating 'list' binding returned a " + wrapped.getClass().getName() + " when it should return java.util.List or an Object[]");
				}
			}
		}

		/**
		 * @return Size of the contained list 
		 */
		private int size() {
			if (list != null) {
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
		private Object get(int i) {
			if (list != null) {
				return list.get(i);
			}
			else if (array != null) {
				return array[i];
			}

			return null;
		}
		
		/**
		 * @return The collection we're working on.
		 */
		private Object wrapped() {
			if( list != null ) {
				return list;
			}
			else if( array != null ) {
				return array;
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
		final String uniqueKeyPath = (String)_uniqueKey.valueInComponent(component);
		final Object uniqueKey = NSKeyValueCodingAdditions.Utility.valueForKeyPath(object, uniqueKeyPath);

		if (uniqueKey == null) {
			throw new IllegalArgumentException("Can't use null as uniqueKey for " + object);
		}
		
		final String key = ERXWOContext.safeIdentifierName(uniqueKey.toString());

		if (_debugHashCodes != null && _debugHashCodes.booleanValueInComponent(component)) {
			log.info("debugHashCodes for '{}', {} = {}", _list.keyPath(), object, key);
		}

		return key;
	}

	/**
	 * Prepares the WOContext for the loop iteration.
	 */
	private void _prepareForIterationWithIndex(ListWrapper list, int index, WOContext context, WOComponent component, boolean checkHashCodes) {
		Object object = null;

		if (_item != null) {
			object = list.get(index);
			_item._setValueNoValidation(object, component);
		}

		if (_index != null) {
			_index._setValueNoValidation(index, component);
		}

		boolean didAppend = false;

		if (checkHashCodes) {
			if (object != null) {
				String elementID = null;
				if (_uniqueKey == null) {
					int hashCode = hashCodeForObject(component, object);
					if (hashCode != 0) {
						elementID = String.valueOf(hashCode);
					}
				}
				else {
					elementID = keyForObject(component, object);
				}

				if (elementID != null) {
					if (index != 0) {
						context.deleteLastElementIDComponent();
					}
					log.debug("prepare {}->{}", elementID, object);
					context.appendElementIDComponent(elementID);
					didAppend = true;
				}
			}
		}

		if (!didAppend) {
			if (index != 0) {
				context.incrementLastElementIDComponent();
			}
			else {
				context.appendZeroElementIDComponent();
			}
		}
	}

	/**
	 * Cleans the WOContext after the loop iteration.
	 */
	private void _cleanupAfterIteration(int i, WOContext context, WOComponent component) {
		if (_item != null) {
			_item._setValueNoValidation(null, component);
		}
		if (_index != null) {
			_index._setValueNoValidation(i, component);
		}
		context.deleteLastElementIDComponent();
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

	private int _count(ListWrapper list, WOComponent component) {
		int count;

		if (_list != null) {
			count = list.size();
		}
		else {
			Object object = _count.valueInComponent(component);

			if (object != null) {
				count = ERXValueUtilities.intValue(object);
			}
			else {
				log.error("{} 'count' evaluated to null in component {}.\nRepetition  count reset to 0.", this, component);
				count = 0;
			}
		}

		return count;
	}

	private ListWrapper listWrapper(WOComponent component) {
		final Object list = _list != null ? _list.valueInComponent(component) : null;

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

		return new ListWrapper(list);
	}

	@Override
	public void takeValuesFromRequest(WORequest request, WOContext context) {
		final WOComponent component = context.component();
		final ListWrapper list = listWrapper(component);

		final int count = _count(list, component);
		final boolean checkHashCodes = checkHashCodes(component);

		if (log.isDebugEnabled()) {
			log.debug("takeValuesFromRequest: {} - {}", context.elementID(), context.request().formValueKeys());
		}

		for (int index = 0; index < count; index++) {
			_prepareForIterationWithIndex(list, index, context, component, checkHashCodes);
			super.takeValuesFromRequest(request, context);
		}

		if (count > 0) {
			_cleanupAfterIteration(count, context, component);
		}
	}

	@Override
	public WOActionResults invokeAction(WORequest request, WOContext context) {
		
		final WOComponent wocomponent = context.component();
		final ListWrapper list = listWrapper(wocomponent);

		final int count = _count(list, wocomponent);

		WOActionResults actionresults = null;
		final String indexString = _indexOfChosenItem(request, context);

		int index = 0;

		final boolean checkHashCodes = checkHashCodes(wocomponent);

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

						for (int i = 0; i < list.size() && !found; i++) {
							Object o = list.get(i);
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
							log.warn("Wrong object: {} vs {} (array = {})", otherHashCode, hashCode, list.wrapped());
						}
					}
					else {
						String key = indexString;
						String otherKey = null;

						for (int i = 0; i < list.size() && !found; i++) {
							Object o = list.get(i);
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
							log.warn("Wrong object: {} vs {} (array = {})", otherKey, key, list.wrapped());
						}
					}

					if (!found) {
						if (raiseOnUnmatchedObject(wocomponent)) {
							throw new UnmatchedObjectException();
						}

						if (_notFoundMarker == null) {
							return context.page();
						}

						object = _notFoundMarker.valueInComponent(wocomponent);
					}
				}
				else {
					if (index >= list.size()) {
						if (raiseOnUnmatchedObject(wocomponent)) {
							throw new UnmatchedObjectException();
						}

						return context.page();
					}

					object = list.get(index);
				}
				_item._setValueNoValidation(object, wocomponent);
			}

			if (_index != null) {
				_index._setValueNoValidation(index, wocomponent);
			}

			context.appendElementIDComponent(indexString);

			log.debug("invokeAction: {}", context.elementID());

			actionresults = super.invokeAction(request, context);
			context.deleteLastElementIDComponent();
		}
		else {
			for (int i = 0; i < count && actionresults == null; i++) {
				_prepareForIterationWithIndex(list, i, context, wocomponent, checkHashCodes);
				actionresults = super.invokeAction(request, context);
			}

			if (count > 0) {
				_cleanupAfterIteration(count, context, wocomponent);
			}
		}

		return actionresults;
	}
	
	@Override
	public void appendToResponse(WOResponse response, WOContext context) {
		
		final WOComponent component = context.component();
		final ListWrapper list = listWrapper(component);

		final int count = _count(list, component);
		final boolean checkHashCodes = checkHashCodes(component);

		log.debug("appendToResponse: {}", context.elementID());

		for (int index = 0; index < count; index++) {
			_prepareForIterationWithIndex(list, index, context, component, checkHashCodes);
			appendChildrenToResponse(response, context);
		}

		if (count > 0) {
			_cleanupAfterIteration(count, context, component);
		}
	}

	private boolean checkHashCodes(WOComponent component) {

		if (_checkHashCodes != null) {
			return _checkHashCodes.booleanValueInComponent(component);
		}

		return _checkHashCodesDefault;
	}

	private boolean raiseOnUnmatchedObject(WOComponent component) {

		if (_raiseOnUnmatchedObject != null) {
			return _raiseOnUnmatchedObject.booleanValueInComponent(component);
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