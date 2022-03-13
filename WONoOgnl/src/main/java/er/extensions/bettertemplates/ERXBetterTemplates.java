/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */

/* WOOgnl.java created by max on Fri 28-Sep-2001 */
package er.extensions.bettertemplates;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver._private.WOBindingNameAssociation;
import com.webobjects.appserver._private.WOConstantValueAssociation;
import com.webobjects.appserver._private.WOKeyValueAssociation;
import com.webobjects.appserver.parser.WOComponentTemplateParser;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSSelector;
import com.webobjects.foundation._NSUtilities;

/**
 * WOOgnl provides a template parser that support WOOgnl associations, Helper Functions, Inline Bindings, and Binding Debugging. 
 * 
 * @property ognl.active - defaults to true, if false ognl support is disabled
 * @property ognl.inlineBindings - if true, inline bindings are supported in component templates
 * @property ognl.parseStandardTags - if true, you can use inline bindings in regular html tags, but requires well-formed templates
 * @property ognl.debugSupport - if true, debug metadata is included in all bindings (but binding debug is not automatically turned on) 
 * 
 * @author mschrag
 */

public class ERXBetterTemplates {
	private static final Logger log = LoggerFactory.getLogger(ERXBetterTemplates.class);

	protected static Collection<Observer> _retainerArray = new NSMutableArray<>();
	static {
		try {
			Observer o = new Observer();
			_retainerArray.add(o);
			NSNotificationCenter.defaultCenter().addObserver(o, new NSSelector("configureWOOgnl", new Class[] { com.webobjects.foundation.NSNotification.class }), WOApplication.ApplicationWillFinishLaunchingNotification, null);
		}
		catch (Exception e) {
			log.error("Failed to configure WOOgnl.", e);
		}
	}

	private static Map<String, Class> associationMappings = new Hashtable<>();

	public static void setAssociationClassForPrefix(Class clazz, String prefix) {
		associationMappings.put(prefix, clazz);
	}

	private WOAssociation createAssociationForClass(Class clazz, String value, boolean isConstant) {
		return (WOAssociation) _NSUtilities.instantiateObject(clazz, new Class[] { Object.class, boolean.class }, new Object[] { value, Boolean.valueOf(isConstant) }, true, false);
	}

	public static class Observer {
		public void configureWOOgnl(NSNotification n) {
			ERXBetterTemplates.factory().configureWOForOgnl();
			NSNotificationCenter.defaultCenter().removeObserver(this);
			_retainerArray.remove(this);
		}
	}

	protected static ERXBetterTemplates _factory;

	public static ERXBetterTemplates factory() {
		if (_factory == null) {
			_factory = new ERXBetterTemplates();
		}
		return _factory;
	}

	public static void setFactory(ERXBetterTemplates factory) {
		_factory = factory;
	}

	public void configureWOForOgnl() {
		// Register template parser
		if (hasProperty("ognl.active", "true")) {
			String parserClassName = System.getProperty("ognl.parserClassName", "ognl.helperfunction.WOHelperFunctionParser54");
			WOComponentTemplateParser.setWOHTMLTemplateParserClassName(parserClassName);

			if (hasProperty("ognl.inlineBindings", "false")) {
				WOHelperFunctionTagRegistry.setAllowInlineBindings(true);
			}
			if (hasProperty("ognl.parseStandardTags", "false")) {
				WOHelperFunctionHTMLParser.setParseStandardTags(true);
			}
			if (hasProperty("ognl.debugSupport", "false")) {
				WOHelperFunctionParser._debugSupport = true;
			}
		}
	}

	private boolean hasProperty(String prop, String def) {
		String property = System.getProperty(prop, def).trim();
		return "true".equalsIgnoreCase(property) || "yes".equalsIgnoreCase(property);
	}

	public void convertOgnlConstantAssociations(NSMutableDictionary associations) {
		for (Enumeration e = associations.keyEnumerator(); e.hasMoreElements();) {
			String name = (String) e.nextElement();
			WOAssociation association = (WOAssociation) associations.objectForKey(name);
			boolean isConstant = false;
			String keyPath = null;
			if (association instanceof WOConstantValueAssociation) {
				WOConstantValueAssociation constantAssociation = (WOConstantValueAssociation) association;
				// AK: this sucks, but there is no API to get at the value
				Object value = constantAssociation.valueInComponent(null);
				keyPath = value != null ? value.toString() : null;
				isConstant = true;
			}
			else if (association instanceof WOKeyValueAssociation) {
				keyPath = association.keyPath();
			}
			else if (association instanceof WOBindingNameAssociation) {
				WOBindingNameAssociation b = (WOBindingNameAssociation) association;
				// AK: strictly speaking, this is not correct, as we only get the first part of 
				// the path. But take a look at WOBindingNameAssociation for a bit of fun...
				keyPath = "^" + b._parentBindingName;
			}
			if (keyPath != null) {
				if (!associationMappings.isEmpty()) {
					int index = name.indexOf(':');
					if (index > 0) {
						String prefix = name.substring(0, index);
						if (prefix != null) {
							Class c = associationMappings.get(prefix);
							if (c != null) {
								String postfix = name.substring(index + 1);
								WOAssociation newAssociation = createAssociationForClass(c, keyPath, isConstant);
								associations.removeObjectForKey(name);
								associations.setObjectForKey(newAssociation, postfix);
							}
						}
					}
				}
			}
		}
	}
}