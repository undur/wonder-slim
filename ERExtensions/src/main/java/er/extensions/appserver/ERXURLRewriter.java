package er.extensions.appserver;

import er.extensions.foundation.ERXProperties;

/**
 * An optional pattern-and-replacement applied to every generated URL (and
 * redirect location). The escape hatch for a front end that maps the
 * application under a path of its own choosing:
 *
 * <pre>
 * er.extensions.ERXApplication.replaceApplicationPath.pattern=/cgi-bin/WebObjects/YourApp.woa
 * er.extensions.ERXApplication.replaceApplicationPath.replace=/yourapp
 * </pre>
 *
 * with the matching front-end rule mapping {@code /yourapp/…} back. For the
 * common case — the application's own request handlers as top-level routes —
 * prefer {@link ERXShortURLs} ({@code er.extensions.ERXApplication.shortURLs}),
 * which needs no pattern and works in both directions.
 *
 * History: Wonder's version of this also synthesized the pattern itself when
 * {@code rewriteDirectConnect} was set in development mode, to strip the
 * prefix from generated URLs while the matching prepend in
 * {@code ERXApplication.createRequest()} put it back on incoming ones — the
 * ancestor of short URLs, tied to development and to direct connect. Short
 * URLs cover that in both modes, so this class is now only the pattern
 * rewrite, and a null pattern makes it inert.
 *
 * @param pattern The regular expression to match in generated URLs, null for none
 * @param replacement What to replace the first match with
 */
public record ERXURLRewriter( String pattern, String replacement ) {

	/**
	 * @return The rewriter configured by the properties, or an inert one
	 */
	public static ERXURLRewriter fromProperties() {
		final String pattern = ERXProperties.stringForKey("er.extensions.ERXApplication.replaceApplicationPath.pattern");
		final String replacement = ERXProperties.stringForKey("er.extensions.ERXApplication.replaceApplicationPath.replace");
		return new ERXURLRewriter( pattern == null || pattern.isEmpty() ? null : pattern, replacement == null ? "" : replacement );
	}

	/**
	 * @return The URL with the first match of the pattern replaced; unchanged when no pattern is configured
	 */
	public String rewriteURL(final String url) {
		if (url != null && pattern != null) {
			return url.replaceFirst(pattern, replacement);
		}
		return url;
	}
}
