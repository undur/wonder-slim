/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.bettertemplates;

import com.webobjects.appserver.parser.WOComponentTemplateParser;

/**
 * Provides a template parser that support Helper Functions, Inline Bindings,
 * and Binding Debugging.
 * 
 * @property bettertemplates.active - defaults to true, if false bettertemplates support is disabled
 * @property bettertemplates.inlineBindings - if true, inline bindings are supported in component templates
 * @property bettertemplates.parseStandardTags - if true, you can use inline bindings in regular html tags, but requires well-formed templates
 * @property bettertemplates.debugSupport - if true, debug metadata is included in all bindings (but binding debug is not automatically turned on)
 * 
 * FIXME: Does the ognl.helperfunctions property still exist somewhere and does it do something?
 */

public class ERXBetterTemplates {

	public static void configureWOForBetterTemplates() {
		if (hasProperty("bettertemplates.active", "true")) {
			String parserClassName = System.getProperty("bettertemplates.parserClassName", WOHelperFunctionParser54.class.getName());
			WOComponentTemplateParser.setWOHTMLTemplateParserClassName(parserClassName);

			if (hasProperty("bettertemplates.inlineBindings", "true")) {
				WOHelperFunctionTagRegistry.setAllowInlineBindings(true);
			}

			if (hasProperty("bettertemplates.parseStandardTags", "false")) {
				WOHelperFunctionHTMLParser.setParseStandardTags(true);
			}
			if (hasProperty("bettertemplates.debugSupport", "false")) {
				WOHelperFunctionParser._debugSupport = true;
			}
		}
	}

	private static boolean hasProperty(String prop, String def) {
		String property = System.getProperty(prop, def).trim();
		return "true".equalsIgnoreCase(property) || "yes".equalsIgnoreCase(property);
	}
}