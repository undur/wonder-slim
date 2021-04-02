/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.foundation;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSSet;

import er.extensions.components.ERXStatelessComponent;
import er.extensions.eof.ERXConstant;

/**
 * Diverse collection of utility methods for handling everything from
 * EOF to foundation. In the future this class will most likely be
 * split into more meaning full groups of utility methods.
 */
public class ERXUtilities {
    private static final Logger log = LoggerFactory.getLogger(ERXUtilities.class);
    
    /**
     * Utility method to get all of the framework names that
     * have been loaded into the application.
     * @return array containing all of the framework names
     */
    public static NSArray allFrameworkNames() {
        NSMutableArray frameworkNames = new NSMutableArray();
        for (Enumeration e = NSBundle.frameworkBundles().objectEnumerator(); e.hasMoreElements();) {
            NSBundle bundle = (NSBundle)e.nextElement();
            if (bundle.name() != null)
                frameworkNames.addObject(bundle.name());
            else
                log.warn("Null framework name for bundle: {}", bundle);
        }
        return frameworkNames;
    }
   
    /**
     * Generates a string representation of the current stacktrace.
     *
     * @return current stacktrace.
     */
    public static String stackTrace() {
        String result = null;
        try {
            throw new Throwable();
        } catch (Throwable t) {
            result = ERXUtilities.stackTrace(t);
        }

        String separator = System.getProperties().getProperty("line.separator");

        // Chop off the 1st line, "java.lang.Throwable"
        //
        int offset = result.indexOf(separator);
        result = result.substring(offset+1);

        // Chop off the lines at the start that refer to ERXUtilities
        //
        offset = result.indexOf(separator);
        while (result.substring(0,offset).indexOf("ERXUtilities.java") >= 0) {
            result = result.substring(offset+1);
            offset = result.indexOf(separator);
        }
        return separator+result;
    }

    /**
     * Converts a throwable's stacktrace into a
     * string representation.
     * @param t throwable to print to a string
     * @return string representation of stacktrace
     */
    public static String stackTrace(Throwable t) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(2048);
        PrintStream printStream = new PrintStream(baos);
        t.printStackTrace(printStream);
        return baos.toString();
    }

    /**
     * Useful interface for binding objects to
     * WOComponent bindings where you want to
     * delay the evaluation of the boolean operation
     * until <code>valueForBinding</code> is
     * actually called. See {@link ERXStatelessComponent}
     * for examples.
     */
    public static interface BooleanOperation {
        public boolean value();
    }

    /**
     * Useful interface for binding objects to
     * WOComponent bindings where you want to
     * delay the evaluation of the operation
     * until <code>valueForBinding</code> is
     * actually called. See {@link ERXStatelessComponent}
     * for examples.
     */
    public static interface Operation {
        public Object value();
    }

    /**
     * Generic callback interface with a context
     * object.
     */
    public static interface Callback {
        public Object invoke(Object ctx);
    }

    /**
     * Generic boolean callback interface with a
     * context object.
     */
    public static interface BooleanCallback {
        public boolean invoke(Object ctx);
    }

    /**
     * Gets rid of all ' from a String.
     * @param aString string to check
     * @return string without '
     */
    // CHECKME: Is this a value add? I don't think so.
    public static String escapeApostrophe(String aString) {
        NSArray parts = NSArray.componentsSeparatedByString(aString,"'");
        return parts.componentsJoinedByString("");
    }
}
