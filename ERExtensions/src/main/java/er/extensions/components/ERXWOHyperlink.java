package er.extensions.components;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOElement;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver.WOResponse;
import com.webobjects.appserver._private.WOHyperlink;
import com.webobjects.appserver._private.WONoContentElement;
import com.webobjects.foundation.NSDictionary;

import er.extensions.appserver.ERXSession;
import er.extensions.foundation.ERXProperties;

/**
 * Enhancement to WOHyperlink. Don't use this class directly, it is patched 
 * automatically into the runtime system on application startup. Just use WOHyperlink.
 * <ul>
 * <li>Puts a description of the action into the session under the key <code>ERXActionLogging</code>
 * <li>When the <code>disabled</code> is true, then returns <code>context().page()</code>
 * instead of the normal WONoContentElement. The rationale is that in almost all cases 
 * you don't want your users to see an empty page, especially not in the typical case when
 * you show a list paginator and disable the link to the current item.
 * <li>When you have action targets inside of the hyperlink, then their invokeAction() 
 * method is normally never called. This is probably an optimization, but breaks the case when you have
 * - say - onClick elements inside of the hyperlink. This subclass will instead propagate the
 * invokeAction to the children if the senderID() starts with the elementID() (which should indicate an
 * action inside of a hyperlink).  
 * </ul>
 * @author david Logging
 * @author ak WONoContentElement fix, senderID fix, double-quote fix
 */

public class ERXWOHyperlink extends WOHyperlink {

	/**
     * Defines if the hyperlink adds a default <code>rel="nofollow"</code> if an action is bound.
     */
    private static boolean defaultNoFollow = ERXProperties.booleanForKey("er.extensions.ERXHyperlink.defaultNoFollow");

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

            if(senderID.startsWith(elementID) && !elementID.equals(senderID)) {
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