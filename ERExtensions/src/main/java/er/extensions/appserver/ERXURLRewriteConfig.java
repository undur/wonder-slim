package er.extensions.appserver;

import er.extensions.foundation.ERXProperties;

/**
 * Holds configuration for an application's URL rewriting
 * 
 * @param replaceApplicationPathPattern The path rewriting pattern to match (@see _rewriteURL)
 * @param replaceApplicationPathReplace The path rewriting replacement to apply to the matched pattern (@see _rewriteURL)
 */

public record ERXURLRewriteConfig( String replaceApplicationPathPattern, String replaceApplicationPathReplace ) {
	
	public ERXURLRewriteConfig( ERXApplication app ) {
		String _replaceApplicationPathPattern = ERXProperties.stringForKey("er.extensions.ERXApplication.replaceApplicationPath.pattern");

		if (_replaceApplicationPathPattern != null && _replaceApplicationPathPattern.length() == 0) {
			_replaceApplicationPathPattern = null;
		}

		String _replaceApplicationPathReplace = ERXProperties.stringForKey("er.extensions.ERXApplication.replaceApplicationPath.replace");

		if (_replaceApplicationPathPattern == null && app.rewriteDirectConnectURL()) {
			_replaceApplicationPathPattern = "/cgi-bin/WebObjects/" + app.name() + app.applicationExtension();

			if (_replaceApplicationPathReplace == null) {
				_replaceApplicationPathReplace = "";
			}
		}
		
		this( _replaceApplicationPathPattern, _replaceApplicationPathReplace );
	}
	
	/**
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
	 * @param url the URL to rewrite
	 * @return the rewritten URL
	 */
	public String rewriteURL(final String url) {

		if (url != null && replaceApplicationPathPattern != null && replaceApplicationPathReplace != null) {
			return url.replaceFirst(replaceApplicationPathPattern, replaceApplicationPathReplace);
		}

		return url;
	}
}