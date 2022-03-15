/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.bettertemplates;

import com.webobjects.appserver.parser.WOComponentTemplateParser;

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

	public static void configureWOForBetterTemplates() {
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

	private static boolean hasProperty(String prop, String def) {
		String property = System.getProperty(prop, def).trim();
		return "true".equalsIgnoreCase(property) || "yes".equalsIgnoreCase(property);
	}
}