package er.extensions.statistics;

import java.util.Collections;
import java.util.Enumeration;

import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSKeyValueCodingAdditions;
import com.webobjects.foundation.NSMutableArray;
import com.webobjects.foundation.NSMutableDictionary;

import er.extensions.components.ERXStatelessComponent;

/**
 * A component to display a summary of collected stats.
 * @author Travis Cripps
 */
public class ERXStatsSummary extends ERXStatelessComponent {

    public String currentType;
    private NSDictionary _statsByType;
    private NSArray _statsTypes;

    public ERXStatsSummary(WOContext context) {
        super(context);
    }

    /**
     * Gets the aggregate stats grouped by event type.
     * @return the aggregate stats
     */
    public NSDictionary statsByType() {
        if (null == _statsByType) {
            if (hasBinding("aggregateStats")) {
                _statsByType = (NSDictionary)valueForBinding("aggregateStats");
            } else {
                NSMutableDictionary dict = new NSMutableDictionary();
                NSArray<ERXStats.LogEntry> entries = ERXStats.aggregateLogEntries();
                for (ERXStats.LogEntry logEntry : entries) {
                    String group = firstPropertyKeyInKeyPath(logEntry.key());
                    NSMutableArray eventsForType = (NSMutableArray)dict.objectForKey(group);
                    if (null == eventsForType) {
                        eventsForType = new NSMutableArray();
                        dict.setObjectForKey(eventsForType, group);
                    }
                    eventsForType.addObject(logEntry);
                }
                _statsByType = dict;
            }
        }
        return _statsByType;
    }

    /**
     * FYI, this was moved here from the old ERXString Utilities 
     */
	private static final String firstPropertyKeyInKeyPath(String keyPath) {
		String part = null;
		if (keyPath != null) {
			int index = keyPath.indexOf(NSKeyValueCodingAdditions.KeyPathSeparator);
			if (index != -1)
				part = keyPath.substring(0, index);
			else
				part = keyPath;
		}
		return part;
	}

    /**
     * Gets the array of stats event types.
     * @return the event types
     */
    public NSArray statsTypes() {
        if (null == _statsTypes) {
            final NSMutableArray<String> keys = new NSMutableArray<>( statsByType().allKeys() );
            Collections.sort( keys );
			_statsTypes = keys.immutableClone();
        }
        return _statsTypes;
    }

    /**
     * Gets the duration for the current stats type.
     * @return the duration
     */
    public long durationForStatsType() {
        long result = 0;
        NSArray statsForType = (NSArray)statsByType().valueForKey(currentType);
        for (Enumeration statsEnum = statsForType.objectEnumerator(); statsEnum.hasMoreElements();) {
            ERXStats.LogEntry logEntry = (ERXStats.LogEntry)statsEnum.nextElement();
            result += logEntry.sum();
        }
        return result;
    }

    /**
     * Determines if there are stats to display.
     * @return true if there are stats
     */
    public boolean hasStats() {
        return statsTypes().count() > 0;
    }

    @Override
    public void reset() {
        super.reset();
        currentType = null;
        _statsByType = null;
        _statsTypes = null;
    }

}
