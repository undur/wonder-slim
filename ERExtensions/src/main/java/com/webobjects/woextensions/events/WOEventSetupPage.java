/*
 * WOEventSetupPage.java
 * (c) Copyright 2001 Apple Computer, Inc. All rights reserved.
 * This a modified version.
 * Original license: http://www.opensource.apple.com/apsl/
 */

package com.webobjects.woextensions.events;

import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;
import com.webobjects.eocontrol.EOEvent;
import com.webobjects.eocontrol.EOEventCenter;
import com.webobjects.eocontrol.EOSortOrdering;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSComparator;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSSelector;

public class WOEventSetupPage extends WOEventPage {

	public Class currentClass;
	public String currentEventDescription;
	public int currentIndex;

	protected static final _ClassNameComparator _classNameAscendingComparator = new _ClassNameComparator(EOSortOrdering.CompareAscending);

	public WOEventSetupPage(WOContext aContext) {
		super(aContext);
	}

	public NSArray registeredEventClasses() {
		NSMutableArray classes;

		classes = new NSMutableArray();
		classes.setArray(EOEventCenter.registeredEventClasses());

		try {
			classes.sortUsingComparator(_classNameAscendingComparator);
		}
		catch (NSComparator.ComparisonException e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}

		return classes;
	}

	public boolean isClassRegisteredForLogging() {
		return EOEventCenter.recordsEventsForClass(currentClass);
	}

	public void setIsClassRegisteredForLogging(boolean yn) {
		EOEventCenter.setRecordsEvents(yn, currentClass);
	}

	protected void _setAllRegisteredEvents(boolean tf) {
		NSArray registered;
		int i, n;
		Class c;

		registered = EOEventCenter.registeredEventClasses();
		int count = registered.count();
		for (i = 0, n = count; i < n; i++) {
			c = (Class) registered.objectAtIndex(i);
			EOEventCenter.setRecordsEvents(tf, c);
		}
	}

	public WOComponent selectAll() {
		_setAllRegisteredEvents(true);
		return null;
	}

	public WOComponent clearAll() {
		_setAllRegisteredEvents(false);
		return null;
	}

	public NSArray currentEventDescriptions() {
		NSMutableArray<String> descs;
		NSDictionary<String, String> map;

		map = EOEvent.eventTypeDescriptions(currentClass);

		descs = new NSMutableArray<>();
		descs.setArray(map.allValues());
		descs.removeObject(map.objectForKey(EOEvent.EventGroupName));
		try {
			descs.sortUsingComparator(NSComparator.AscendingStringComparator);
		}
		catch (NSComparator.ComparisonException e) {
			throw NSForwardException._runtimeExceptionForThrowable(e);
		}
		descs.insertObjectAtIndex(map.objectForKey(EOEvent.EventGroupName), 0);

		return descs;
	}

	public boolean isClassName() {
		return (currentIndex == 0);
	}

	private static class _ClassNameComparator extends NSComparator {
		protected boolean _compareAscending;

		public _ClassNameComparator(NSSelector comparator) {
			super();
			_compareAscending = (comparator == EOSortOrdering.CompareAscending);
		}

		@Override
		public int compare(Object c1, Object c2) throws NSComparator.ComparisonException {
			if (!(c1 instanceof Class) || !(c2 instanceof Class) || (c1 == null) || (c2 == null))
				throw new NSComparator.ComparisonException("<" + getClass().getName() + " Unable to compare classes. Either one of the arguments is not a Class or is null. Comparison was made with " + c1 + " and " + c2 + ".");

			Class class1, class2;
			class1 = (Class) c1;
			class2 = (Class) c2;

			int result = class1.getName().compareTo(class2.getName());
			if (result == 0) {
				return result;
			}
			if (!_compareAscending) {
				result = 0 - result;
			}
			return result > 0 ? 1 : -1;
		}
	}
}