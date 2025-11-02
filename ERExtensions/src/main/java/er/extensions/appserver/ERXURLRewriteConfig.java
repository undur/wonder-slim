package er.extensions.appserver;

import er.extensions.foundation.ERXProperties;

/**
 * Holds configuration for an application's URL rewriting
 * 
 * Hook to rewrite generated URLs. Invoked by ERXWOContext.
 * 
 * You can also set "er.extensions.replaceApplicationPath.pattern" to the pattern
 * to match and "er.extensions.replaceApplicationPath.replace" to the value to replace it with.
 * 
 * For example, in Properties: <code>
 * er.extensions.ERXApplication.replaceApplicationPath.pattern=/cgi-bin/WebObjects/YourApp.woa
 * er.extensions.ERXApplication.replaceApplicationPath.replace=/yourapp
 * </code>
 * 
 * and in Apache 2.2: <code>
 * RewriteRule ^/yourapp(.*)$ /cgi-bin/WebObjects/YourApp.woa$1 [PT,L]
 * </code>
 * 
 * @param pattern The pattern to match from the URL
 * @param replacement The string to replace the matched pattern with
 */

public record ERXURLRewriteConfig( String pattern, String replacement ) {
	
	public ERXURLRewriteConfig( ERXApplication app ) {
		String propPattern = ERXProperties.stringForKey("er.extensions.ERXApplication.replaceApplicationPath.pattern");
		String propReplacement = ERXProperties.stringForKey("er.extensions.ERXApplication.replaceApplicationPath.replace");

		if (propPattern != null && propPattern.length() == 0) {
			propPattern = null;
		}

		if (propPattern == null && app.shouldRewriteDirectConnectURL()) {
			propPattern = "/cgi-bin/WebObjects/" + app.name() + app.applicationExtension();

			if (propReplacement == null) {
				propReplacement = "";
			}
		}
		
		this( propPattern, propReplacement );
	}
	
	/**
	 * @return Rewritten URL
	 */
	public String rewriteURL(final String url) {

		if (url != null && pattern != null && replacement != null) {
			return url.replaceFirst(pattern, replacement);
		}

		return url;
	}
}