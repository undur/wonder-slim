/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.validation;

import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSKeyValueCoding;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSValidation;

import er.extensions.foundation.ERXStringUtilities;
import er.extensions.localization.ERXLocalizer;

/**
 * This is more of a legacy object that was used until
 * we had {@link ERXValidationFactory ERXValidationFactory} in place.
 * The only place where this is used is when handling
 * {@link com.webobjects.foundation.NSValidation.ValidationException ValidationExceptions}
 * that have not been converted by the ERXValidationFactory. This class is
 * also used to handle formatter exceptions that are thrown number formatters in
 * WOComponents.
 */
public class ERXValidation {
    private static final Logger log = LoggerFactory.getLogger("er.validation.ERValidation");

    /** holds the static constant for pushing an incorrect value onto an eo */
    public final static boolean PUSH_INCORRECT_VALUE_ON_EO=true;

    /** holds the static constant for not pushing an incorrect value onto an eo */
    public final static boolean DO_NOT_PUSH_INCORRECT_VALUE_ON_EO=false;

    /** holds the default constant for pushing on values onto eos, defaults to false */
    private static boolean pushChangesDefault = DO_NOT_PUSH_INCORRECT_VALUE_ON_EO;

    /**
     * Sets pushing changes onto enterprise objects when a
     * validation exception occurs.
     * @param val sets whether incorrect values should be pushed
     *		onto an object
     */
    public static void setPushChangesDefault(boolean val) {
        pushChangesDefault = val;
    }

    /**
     * Processes a validation exception to make it look better.
     * The resulting exception message is set in the errorMessages
     * dictionary.
     *
     * @param e validation exception.
     * @param value that failed validation.
     * @param keyPath that failed validation.
     * @param errorMessages dictionary to place the formatted message into.
     * @param displayPropertyKeyPath key used in the case of the formatter exception
     *		to calculate the pretty display name.
     * @param localizer to use to localize the exception.
     */    
    public static void validationFailedWithException(Throwable e,
                                                     Object value,
                                                     String keyPath,
                                                     NSMutableDictionary errorMessages,
                                                     String displayPropertyKeyPath,
                                                     ERXLocalizer localizer) {
        validationFailedWithException(e,value,keyPath,errorMessages,displayPropertyKeyPath,localizer,null);
    }

    /**
     * Processes a validation exception to make it look better.
     * The resulting exception message is set in the errorMessages
     * dictionary. This method uses the default value for pushing values
     * onto the eo.
     *
     * @param e validation exception.
     * @param value that failed validation.
     * @param keyPath that failed validation.
     * @param errorMessages dictionary to place the formatted message into.
     * @param displayPropertyKeyPath key used in the case of the formatter exception
     *		to calculate the pretty display name.
     * @param localizer to use to localize the exception.
     * @param entity that the validation exception is happening too.
     */    
    public static void validationFailedWithException(Throwable e,
                                                     Object value,
                                                     String keyPath,
                                                     NSMutableDictionary errorMessages,
                                                     String displayPropertyKeyPath,
                                                     ERXLocalizer localizer,
                                                     Object oldEntity /* FIXME: This used to be an EOEntity */ ) {
        validationFailedWithException(e,value,keyPath,errorMessages, displayPropertyKeyPath, localizer, pushChangesDefault);
    }

    /**
     * Processes a validation exception to make it look better.
     * The resulting exception message is set in the errorMessages
     * dictionary.
     *
     * @param e validation exception.
     * @param value that failed validation.
     * @param keyPath that failed validation.
     * @param errorMessages dictionary to place the formatted message into.
     * @param displayPropertyKeyPath key used in the case of the formatter exception
     *		to calculate the pretty display name.
     * @param localizer to use to localize the exception.
     * @param entity that the validation exception is happening too.
     * @param pushChanges boolean to flag if the bad values should be pushed onto the
     *		eo.
     */
    public static void validationFailedWithException(Throwable e,
                                                     Object value,
                                                     String keyPath,
                                                     NSMutableDictionary errorMessages,
                                                     String displayPropertyKeyPath,
                                                     ERXLocalizer localizer,
                                                     Object oldEntity /* FIXME: This used to be an EOEntity */,
                                                     boolean pushChanges) {
        if (log.isDebugEnabled())
            log.debug("ValidationFailedWithException: {} message: {}", e.getClass(), e.getMessage());
        String key = null;
        String newErrorMessage=e.getMessage();
        if (e instanceof NSValidation.ValidationException && ((NSValidation.ValidationException)e).key() != null
            && ((NSValidation.ValidationException)e).object() != null) {
            NSValidation.ValidationException nve = (NSValidation.ValidationException)e;
            key = nve.key();
            Object eo=nve.object();
            // this because exceptions raised by formatters have the failing VALUE in this key..
            // strip the exception name
            //newErrorMessage=newErrorMessage.substring(newErrorMessage.indexOf(":")+1);
            //newErrorMessage=newErrorMessage.substring(newErrorMessage.indexOf(":")+1);
            //the exception is coming from a formatter
            key = NSArray.componentsSeparatedByString(displayPropertyKeyPath,".").lastObject();
            newErrorMessage="<b>"+key+"</b>:"+newErrorMessage;
        } else {
            key = keyPath;
        }
        if (key != null && newErrorMessage != null) {
        	  String displayName = localizedDisplayNameForKey(key, localizer);
            errorMessages.setObjectForKey(newErrorMessage, displayName);
        } else {
            if(key != null) {
                //log.warn("NULL message for key:'"+key+"': " + ((EOGeneralAdaptorException)e).userInfo() , e);
                log.warn("NULL message for key:'{}'", key, e);
                
            } else {
                log.warn("NULL key for message:'{}'", newErrorMessage, e);
            }
        }
    }
    
    /**
     * Calculates a localized display name for a given entity and key using the supplied localizer
     * @param ecd class description the key belongs to
     * @param key to localize
     * @param localizer to use for localizing the content
     * @return the localized display name
     */
	public static String localizedDisplayNameForKey( String key, ERXLocalizer localizer) {
		String displayName;
		if (localizer != null) {
			displayName = localizer.localizedStringForKeyWithDefault(key);
		} else {
	    	displayName = ERXStringUtilities.displayNameForKey(key);
		}
		return displayName;
	}
}
