/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.validation;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSNotification;
import com.webobjects.foundation.NSNotificationCenter;
import com.webobjects.foundation.NSSelector;

import er.extensions.foundation.ERXSimpleTemplateParser;
import er.extensions.foundation.ERXUtilities;
import er.extensions.localization.ERXLocalizer;

/**
 * The validation factory controls creating validation
 * exceptions, both from model thrown exceptions and 
 * custom validation exceptions. The factory is responsible
 * for resolving validation templates for validation
 * exceptions and generating validation messages.
 */
public class ERXValidationFactory {
    private final static Logger log = LoggerFactory.getLogger(ERXValidationFactory.class);
    
    /** holds a reference to the default validation factory */
    private static ERXValidationFactory _defaultFactory;
    
    /** holds the value 'ValidationTemplate.' */
    public static final String VALIDATION_TEMPLATE_PREFIX = "ValidationTemplate.";

    /** holds the marker for an undefined validation template */
    private final static String UNDEFINED_VALIDATION_TEMPLATE = "Undefined Validation Template";
    
    /**
     * Sets the default factory to be used for converting
     * model thrown exceptions.
     * @param aFactory new factory
     */
    public static void setDefaultFactory(ERXValidationFactory aFactory) { _defaultFactory = aFactory; }
    
    /**
     * Returns the default factory. If one has not
     * been set then a factory is created of type
     * ERXValidationFactory.
     * @return the default validation factory
     */
    public static ERXValidationFactory defaultFactory() {
        if (_defaultFactory == null)
            setDefaultFactory(new ERXValidationFactory());
        return _defaultFactory;
    }

    /** holds the template cache for a given set of keys */
    private Map<ERXMultiKey, String> _cache = new Hashtable<>(1000);

    /** holds the default template delimiter, "@@" */
    private String _delimiter = "@@";

    /**
     * Entry point for generating an exception message for a given message. The method <code>getMessage</code>
     * off of {@link ERXValidationException ERXValidationException} calls this method passing in itself as the parameter.
     * 
     * @param erv validation exception
     * @return a localized validation message for the given exception
     */
    // FIXME: Right now the delegate methods are implemented as a formal interface.  Not ideal.  Should be implemented as an informal interface.  Can still return null to not have an effect.
    public String messageForException(ERXValidationException erv) {
        String message = null;

    	Object context = erv.context();

    	// AK: as the exception doesn't have a very special idea in how the message should get 
    	// formatted when gets displayed, we ask the context *first* before asking the exception.
    	String template = templateForException(erv);

    	if(template.startsWith(UNDEFINED_VALIDATION_TEMPLATE)) {
            // try to get the actual exception message if one is set
    	    message = erv._getMessage();

    	    if(message == null) {
    	        message = template;
    	    }
    	}
    	else {

    	    if(context == erv || context == null) {
    	        message = ERXSimpleTemplateParser.sharedInstance().parseTemplateWithObject( template, templateDelimiter(), erv);
    	    } else {
    	        message = ERXSimpleTemplateParser.sharedInstance().parseTemplateWithObject( template,  templateDelimiter(), context, erv);
    	    }
    	}

        return message;
    }

    /**
     * Entry point for finding a template for a given validationexception.
     * Override this method to provide your own template resolution scheme.
     * 
     * @param erv validation exception
     * @return validation template for the given exception
     */
    public String templateForException(ERXValidationException erv) {
        String template = null;

//      String entityName = erv.eoObject() == null ? null : erv.eoObject().entityName(); FIXME: Getting EOEnterpriseObject out of the way

        String entityName = null;
        String property = erv.isCustomMethodException() ? erv.method() : erv.propertyKey();
        String type = erv.type();
        String targetLanguage = erv.targetLanguage();

        if (targetLanguage == null) {
            targetLanguage = ERXLocalizer.currentLocalizer() != null ? ERXLocalizer.currentLocalizer().language() : ERXLocalizer.defaultLanguage();
        }
        
        log.debug("templateForException with entityName: {}; property: {}; type: {}; targetLanguage: {}", entityName, property, type, targetLanguage);
        ERXMultiKey k = new ERXMultiKey (new Object[] {entityName, property, type,targetLanguage});
        template = _cache.get(k);

        // Not in the cache.  Simple resolving.
        if (template == null) {
            template = templateForEntityPropertyType(entityName, property, type, targetLanguage);
            _cache.put(k, template);
        }

        return template;
    }

    /**
     * Called when the Localizer is reset. This will reset the template cache.
     * 
     * @param n notification posted when the localizer is reset.
     */
    public void resetTemplateCache(NSNotification n) {
        _cache = new Hashtable<>(1000);
        log.debug("Resetting template cache");
    }
    
    /**
     * Returns the template delimiter, the default delimiter is "@@".
     * 
     * @return template delimiter
     */
    public String templateDelimiter() {
    	return _delimiter;
    }
    
    /**
     * Sets the template delimiter to be used when parsing templates for creating validation exception messages.
     * 
     * @param delimiter to be set
     */
    public void setTemplateDelimiter(String delimiter) {
    	_delimiter = delimiter;
    }

    /**
     * Method used to configure the validation factory for operation. This method is called on the default
     * factory from an observer when the application is finished launching.
     */
    public void configureFactory() {
        if (WOApplication.application()!=null && !WOApplication.application().isCachingEnabled()) {
            NSNotificationCenter center = NSNotificationCenter.defaultCenter();
            center.addObserver(this,
                               new NSSelector("resetTemplateCache",  ERXUtilities.NotificationClassArray),
                               ERXLocalizer.LocalizationDidResetNotification,
                               null);
        }
    }

    /**
     * Finds a template for a given entity, property key, exception type and target
     * language. This method provides the defaulting behaviour needed to handle model
     * thrown validation exceptions.
     * 
     * @param entityName name of the entity
     * @param property key name
     * @param type validation exception type
     * @param targetLanguage target language name
     * @return a template for the given set of parameters
     */
    protected String templateForEntityPropertyType(String entityName,
                                                   String property,
                                                   String type,
                                                   String targetLanguage) {
        log.debug("Looking up template for entity named '{}' property '{}' type '{}' target language '{}'.", entityName, property, type, targetLanguage);
        // 1st try the whole string.
        String template = templateForKeyPath(entityName + "." + property + "." + type, targetLanguage);
        // 2nd try everything minus the type.
        if (template == null)
            template = templateForKeyPath(entityName + "." + property, targetLanguage);
        // 2.5th try entity plus type
        if (template == null)
            template = templateForKeyPath(entityName + "." + type, targetLanguage);
        // 3rd try property plus type
        if (template == null)
            template = templateForKeyPath(property + "." + type, targetLanguage);
        // 4th try just property
        if (template == null)
            template = templateForKeyPath(property, targetLanguage);
        // 5th try just type
        if (template == null)
            template = templateForKeyPath(type, targetLanguage);
        if (template == null) {
            template = UNDEFINED_VALIDATION_TEMPLATE + " entity \"" + entityName + "\" property \"" + property + "\" type \"" + type + "\" target language \"" + targetLanguage + "\"";
            log.error(template, new Throwable());
        }
        return template;
    }

    /**
     * Get the template for a given key in a given language. Uses {@link ERXLocalizer} to handle the actual lookup.
     * 
     * @param key the key to lookup
     * @param language use localizer for this language
     * @return template for key or <code>null</code> if none is found
     */
    public String templateForKeyPath(String key, String language) {
        return (String)ERXLocalizer.localizerForLanguage(language).valueForKey(VALIDATION_TEMPLATE_PREFIX + key);
    }
    
    /**
     * FIXME: This class was previously a public class with it's own file. Demoted to here, since that's the only usage.
     * 
     * Simple class to use multiple objects as
     * a single key for a dictionary or HashMap.
     * The goal of this class is to be very fast.
     */
    private static class ERXMultiKey {

        /** holds the object array of keys */
        private Object[] _keys;
        /** caches the number of keys */
        private short _keyCount;

        private int _hashCode;
        
        /**
         * Constructs a multi-key for a given
         * number.
         * @param keyCount number of keys
         */
        private ERXMultiKey(short keyCount) {
            _keyCount=keyCount;
            _keys=new Object[keyCount];
        }

        /**
         * Constructs a multi-key for a given
         * object array.
         * @param keys object array
         */
        public ERXMultiKey(Object[] keys) {
            this((short)keys.length);
            System.arraycopy(keys,0,_keys,0,_keyCount);
            recomputeHashCode();
        }

        /**
         * Calculates a unique hash code for
         * the given array of keys.
         * @return unique hash code for the array
         *		of keys.
         */
        @Override
        public final int hashCode() {
            return _hashCode;
        }
        
        /**
         * Recomputes the hash code if you ever changes the keys array directly 
         */
        public final void recomputeHashCode() {
            int result = 0;

            for (int i=0; i<_keyCount; i++) {
    		    final Object theKey = _keys[i];

                if ( theKey != null ) {
                    result ^= theKey.hashCode();
                    result = ( result << 1 ) | ( result >>> 31 );
                }
            }

            _hashCode = result;
        }

        /**
         * Method used to compare two ERXMultiKeys.
         * A multi key is equal to another multi key
         * if the number of keys are equal and all
         * of the keys are either both null or <code>
         * equals</code>.
         * @param o object to be compared
         * @return result of comparison
         */
        @Override
        public final boolean equals(Object o) {
        	if (o instanceof ERXMultiKey) {
        		ERXMultiKey o2 = (ERXMultiKey) o;
        		if (this == o2)
        			return true;
        		if (_keyCount!=o2._keyCount)
        			return false;
        		if (hashCode()!=o2.hashCode())
        			return false;
        		for (int i=0; i<_keyCount; i++) {
        			Object k=o2._keys[i];
        			Object m=_keys[i];
        			if (m!=k && (m==null || k==null || !m.equals(k)))
        				return false;
        		}
        		return true;
        	}
        	return false;
        }

    	@Override
    	public String toString() {
    		return "ERXMultiKey [_keys=" + Arrays.toString(_keys) + ", _keyCount=" + _keyCount + ", _hashCode=" + _hashCode + "]";
    	}
    }
}
