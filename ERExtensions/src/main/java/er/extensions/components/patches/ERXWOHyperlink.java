package er.extensions.components.patches;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WOHyperlink;
import com.webobjects.appserver._private.WONoContentElement;
import com.webobjects.foundation.NSDictionary;

import er.extensions.ERXP;
import er.extensions.appserver.ERXSession;
import er.extensions.foundation.ERXProperties;

/**
 * Patch of WOHyperlink, installed in its place:
 * <ul>
 * <li>Puts a description of the action into the session under the key <code>ERXActionLogging</code>
 * <li>When the <code>disabled</code> is true, then returns <code>context().page()</code>
 * instead of the normal WONoContentElement. The rationale is that in almost all cases 
 * you don't want your users to see an empty page, especially not in the typical case when
 * you show a list paginator and disable the link to the current item.
 * <li>When you have action targets inside of the hyperlink, then their invokeAction()
 * method is never called: WO's WOHyperlink only handles its own element ID and doesn't pass the action on to
 * its children, which breaks the case when you have - say - onClick elements inside of the hyperlink. This
 * subclass will instead propagate the invokeAction to the children if the senderID() is inside the elementID()
 * (starts with it, followed by a dot), which indicates an action inside of the hyperlink.
 * <li>With er.extensions.ERXHyperlink.defaultNoFollow, a link bound to an action gets {@code rel="nofollow"},
 * keeping crawlers off component action URLs.
 * </ul>
 * @author david Logging
 * @author ak WONoContentElement fix, senderID fix
 */

public class ERXWOHyperlink extends WOHyperlink {

	/**
     * Defines if the hyperlink adds a default <code>rel="nofollow"</code> if an action is bound.
     */
    private static final boolean defaultNoFollow = ERXProperties.booleanForKey(ERXP.HYPERLINK_DEFAULT_NO_FOLLOW.id());

    public ERXWOHyperlink(String name, NSDictionary associations, WOElement template) {
        super(name, associations, template);
    }

    /**
     * Overridden to perform the logging, propagating the action to subelements and returning the
     * current page if an empty page is returned from super.
     */
    @Override
    public WOActionResults invokeAction(WORequest request, WOContext context) {
        WOActionResults result = super.invokeAction(request, context);

        if(result != null && (result instanceof WONoContentElement)) {
            result = context.page();
        }

        if(result == null) {
            final String senderID = context.senderID();
            final String elementID = context.elementID();

            if(senderID.startsWith(elementID + ".")) {
                result = invokeChildrenAction(request, context);
            }
        }

        if (result != null && ERXSession.anySession() != null) {
        	ERXSession.anySession().setObjectForKey(toString(), "ERXActionLogging");
        }

        return result;
    }
 
    @Override
    public void appendAttributesToResponse(final WOResponse response, WOContext context) {
    	super.appendAttributesToResponse(response, context);

    	if (defaultNoFollow && _action != null) {
    		response.appendContentString(" rel=\"nofollow\"");
    	}
    }
}