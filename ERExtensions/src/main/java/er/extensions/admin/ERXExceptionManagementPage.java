package er.extensions.admin;

import java.util.List;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOComponent;
import com.webobjects.appserver.WOContext;

import er.extensions.appserver.ERXApplication;
import er.extensions.appserver.ERXExceptionManager.LoggedException;

/**
 * For viewing the exceptions kept track of in ERXExceptionManager 
 */

public class ERXExceptionManagementPage extends WOComponent {

	public LoggedException current;
	public LoggedException selected;

	public Class<?> currentExceptionClass;
	public Class<?> selectedExceptionClass;

    public ERXExceptionManagementPage(WOContext context) {
        super(context);
    }
    
    public List<LoggedException> exceptions() {
    	List<LoggedException> list = ERXApplication
    			.erxApplication()
    			.exceptionManager()
    			.loggedExceptions();
    	
    	if( selectedExceptionClass != null ) {
    		list = list
    				.stream()
    				.filter(p -> p.throwable().getClass().equals(selectedExceptionClass))
    				.toList();
    	}
    	
    	return list.reversed();
    }
    
    public WOActionResults filter() {
    	selected = null;
    	return null;
    }

    public WOActionResults selectException() {
    	selected = current;
    	return null;
    }
}