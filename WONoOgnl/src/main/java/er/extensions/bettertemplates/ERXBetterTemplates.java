/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.bettertemplates;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOAssociation;
import com.webobjects.appserver.parser.WOComponentTemplateParser;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSSelector;
import com.webobjects.foundation._NSUtilities;

/**
 * Provides a template parser that support Helper Functions, Inline Bindings, and Binding Debugging. 
 * 
 * @property ognl.active - defaults to true, if false ognl support is disabled
 * @property ognl.inlineBindings - if true, inline bindings are supported in component templates
 * @property ognl.parseStandardTags - if true, you can use inline bindings in regular html tags, but requires well-formed templates
 * @property ognl.debugSupport - if true, debug metadata is included in all bindings (but binding debug is not automatically turned on) 
 * 
 * FIXME: We should probably rename these properties eventually… Keeping them now for nice compatibility. 
 */

public class ERXBetterTemplates {
	private static final Logger log = LoggerFactory.getLogger(ERXBetterTemplates.class);

	protected static Collection<Observer> _retainerArray = new NSMutableArray<>();
	static {
		try {
			Observer o = new Observer();
			_retainerArray.add(o);
			NSNotificationCenter.defaultCenter().addObserver(o, new NSSelector("configureWOForBetterTemplates", new Class[] { com.webobjects.foundation.NSNotification.class }), WOApplication.ApplicationWillFinishLaunchingNotification, null);
		}
		catch (Exception e) {
			log.error("Failed to configure ERXBetterTemplates", e);
		}
	}

	private WOAssociation createAssociationForClass(Class clazz, String value, boolean isConstant) {
		return (WOAssociation) _NSUtilities.instantiateObject(clazz, new Class[] { Object.class, boolean.class }, new Object[] { value, Boolean.valueOf(isConstant) }, true, false);
	}

	public static class Observer {
		public void configureWOForBetterTemplates(NSNotification n) {
			ERXBetterTemplates.factory().configureWOForBetterTemplates();
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

	public void configureWOForBetterTemplates() {
		// Register template parser
		if (hasProperty("ognl.active", "true")) {
			String parserClassName = System.getProperty("ognl.parserClassName", "er.extensions.bettertemplates.WOHelperFunctionParser54");
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
}