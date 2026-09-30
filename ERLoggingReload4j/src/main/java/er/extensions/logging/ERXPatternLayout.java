/*
 * Copyright (C) NetStruxr, Inc. All rights reserved.
 *
 * This software is published under the terms of the NetStruxr
 * Public Software License version 0.5, a copy of which has been
 * included with this distribution in the LICENSE.NPL file.  */
package er.extensions.logging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.FormattingInfo;
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.helpers.PatternParser;
import org.apache.log4j.spi.LoggingEvent;

import com.webobjects.appserver.WOAdaptor;
import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSKeyValueCoding;

import er.extensions.foundation.ERXThreadStorage;
import er.extensions.foundation.ERXUtilities;

/**
 * The ERXPatternLayout adds some additional (and needed) layout options. The
 * first is by specifying an '@' character a full backtrace will be logged as
 * part of the log event. The second is by specifying an '$' char the current
 * application name of the WOApplication will be logged as part of the log
 * event. Finally by specifying an '#' char the current port number on which the
 * primary adaptor listens to will be logged as part of the log event.
 * 
 * <pre>
 * WebObjects Application Info Patterns
 * Example: %W{n[i:p s]} -- MyApp[9300:2001 28] 
 * 
 * n: application name
 * i: pid (process ID, provided through Java system property &quot;com.webobjects.pid&quot;) 
 * p: primary adaptor's port number
 * s: active session count
 * 
 * Java VM (Virtual Machine) Info Patterns
 * Example: %V{u used/f free} -- 75.22 MB used/12.86 MB free
 * 
 * t: total memory
 * u: used memory 
 * f: free memory in the current heap size (not max)
 * m: max memory
 * </pre>
 */

// ENHANCEME: Need access to ERXThreadStorage, also need more WO stuff, could opt for a WO char
// and then specify all of the things to log as formatting info for that converter.

public class ERXPatternLayout extends PatternLayout {

	/**
	 * 
	 * Used to update the layout pattern at runtime from the log4j configuration
	 * page
	 * 
	 */
	private static ERXPatternLayout _instance;

	public static ERXPatternLayout instance() {
		if (ERXPatternLayout._instance == null) {
			ERXPatternLayout._instance = new ERXPatternLayout();
		}
		return ERXPatternLayout._instance;
	}

	/**
	 * Default constructor. Uses the default conversion pattern.
	 */
	public ERXPatternLayout() {
		this(PatternLayout.DEFAULT_CONVERSION_PATTERN);
	}

	/**
	 * Default constructor. Uses the specified conversion pattern.
	 * 
	 * @param pattern
	 *            layout to be used.
	 */
	public ERXPatternLayout(String pattern) {
		super(pattern);
		ERXPatternLayout._instance = this; // log4j will create one of these at
											// runtime, and instance() will be
											// used to find it from the log4j
											// config page
	}

	/**
	 * Creates a pattern parser for the given pattern. This method is called
	 * implicitly by the log4j logging system.
	 * 
	 * @param pattern
	 *            to create the pattern parser for
	 * @return an ERXPatternParser for the given pattern
	 */
	@Override
	public PatternParser createPatternParser(String pattern) {
		return new ERXPatternParser(pattern == null ? PatternLayout.DEFAULT_CONVERSION_PATTERN : pattern);
	}
}

/**
 * Pattern parser extension that adds support for WebObjects specific patterns.
 */
class ERXPatternParser extends PatternParser {

	/**
	 * Default constructor for a given pattern
	 * 
	 * @param pattern
	 *            to construct the parser for
	 */
	public ERXPatternParser(String pattern) {
		super(pattern);
	}

	/**
	 * Creates a converter for a particular character. This is the method that
	 * adds the custom converters for WO.
	 * 
	 * @param c
	 *            char to add the converter for
	 */
	@Override
	public void finalizeConverter(char c) {
		switch (c) {
		case '$':
			addConverter(new AppNamePatternConverter(formattingInfo));
			currentLiteral.setLength(0);
			break;
		case '#':
			addConverter(new AdaptorPortNumberConverter(formattingInfo));
			currentLiteral.setLength(0);
			break;
		case '@':
			addConverter(new StackTracePatternConverter(formattingInfo));
			currentLiteral.setLength(0);
			break;
		case 'W':
			addConverter(new AppInfoPatternConverter(formattingInfo, extractOption()));
			currentLiteral.setLength(0);
			break;
		case 'V':
			addConverter(new JavaVMInfoPatternConverter(formattingInfo, extractOption()));
			currentLiteral.setLength(0);
			break;
		case 'T':
			addConverter(new ThreadStoragePatternConverter(formattingInfo, extractOption()));
			currentLiteral.setLength(0);
			break;
		default:
			super.finalizeConverter(c);
			break;
		}
	}

	/**
	 * The stack trace pattern is useful for logging full stack traces when a
	 * log event occurs.
	 */
	private class StackTracePatternConverter extends PatternConverter {
		/**
		 * Package access level constructor.
		 * 
		 * @param formattingInfo
		 *            that is currently being used for this pattern converter.
		 */
		StackTracePatternConverter(FormattingInfo formattingInfo) {
			super(formattingInfo);
		}

		/**
		 * @return The call stack of the logging call: the frames below the last one of the logging libraries (log4j and
		 *         slf4j), a {@code \tat ...} line each
		 */
		@Override
		public String convert(LoggingEvent event) {
			final List<StackTraceElement> frames = StackWalker.getInstance().walk( stream -> stream.map( StackWalker.StackFrame::toStackTraceElement ).toList() );
			int firstCaller = 0;

			for( int i = 0; i < frames.size(); i++ ) {
				final String className = frames.get( i ).getClassName();

				if( className.startsWith( "org.apache.log4j." ) || className.startsWith( "org.slf4j." ) ) {
					firstCaller = i + 1;
				}
			}

			final StringBuilder trace = new StringBuilder();

			for( final StackTraceElement frame : frames.subList( firstCaller, frames.size() ) ) {
				trace.append( "\tat " ).append( frame ).append( '\n' );
			}

			return trace.toString();
		}
	}

	/**
	 * The application name pattern converter is useful for logging the current
	 * application name in log statements.
	 * 
	 * @deprecated
	 */
	@Deprecated
	private class AppNamePatternConverter extends PatternConverter {
		/** holds a reference to the app name */
		String _appName;

		/**
		 * Default package level constructor
		 * 
		 * @param formattingInfo
		 *            current pattern formatting information
		 */
		AppNamePatternConverter(FormattingInfo formattingInfo) {
			super(formattingInfo);
		}

		/**
		 * Returns the current application name for the current logging event.
		 * If the application instance has not been created yet then "N/A" is
		 * logged.
		 * 
		 * @param event
		 *            a given logging event
		 * @return the current application name
		 */
		@Override
		public String convert(LoggingEvent event) {
			if (_appName == null) {
				if (WOApplication.application() != null) {
					_appName = WOApplication.application().name();
				}
			}
			return _appName != null ? _appName : "N/A";
		}
	}

	private class ThreadStoragePatternConverter extends PatternConverter {
		private String key;
		private NSArray keyParts;
		private boolean isKeyPath;

		ThreadStoragePatternConverter(FormattingInfo formattingInfo, String key) {
			super(formattingInfo);
			this.key = key;
			isKeyPath = key != null && key.indexOf(".") != -1;
			if (isKeyPath) {
				keyParts = NSArray.componentsSeparatedByString(key, ".");
			}
		}

		@Override
		public String convert(LoggingEvent event) {
			Object value = null;
			if (!isKeyPath) {
				value = key != null ? ERXThreadStorage.valueForKey(key) : ERXThreadStorage.map();
			}
			else {
				value = ERXThreadStorage.map();
				for (int j = 0; j < keyParts.count(); j++) {
					String part = (String) keyParts.objectAtIndex(j);
					if (j == 0) {
						value = ERXThreadStorage.valueForKey(part);
					}
					else {
						try {
							value = NSKeyValueCoding.Utility.valueForKey(value, part);
						}
						catch (Throwable t) {
							value = "ERR: " + part + " ->" + t.getMessage();
						}
					}
					if (value == null) {
						break;
					}
				}
			}
			return value != null ? value.toString() : null;
		}
	}

	/**
	 * The adaptor port number pattern converter is useful for logging the
	 * current primary adaptor port in log statements.
	 * 
	 * @deprecated
	 */
	@Deprecated
	private class AdaptorPortNumberConverter extends PatternConverter {
		/** holds a reference to the primary adaptor port */
		String _portNumber;

		/**
		 * Default package level constructor
		 * 
		 * @param formattingInfo
		 *            current pattern formatting information
		 */
		AdaptorPortNumberConverter(FormattingInfo formattingInfo) {
			super(formattingInfo);
		}

		/**
		 * Returns the current port number on which the primary adaptor listens
		 * to. This will be the same number specified by WOPort launch argument.
		 * <p>
		 * If the application or adapter instance has not been created yet then
		 * "N/A" is logged.
		 * 
		 * @param event
		 *            a given logging event
		 * @return the current application name
		 */
		@Override
		public String convert(LoggingEvent event) {
			if (_portNumber == null) {
				if (WOApplication.application() != null) {
					// _portNumber =
					// WOApplication.application().port().toString();

					// WO 5.1.x -- Apple Ref# 2260519
					NSArray adaptors = WOApplication.application().adaptors();
					if (adaptors != null && adaptors.count() > 0) {
						WOAdaptor primaryAdaptor = (WOAdaptor) adaptors.objectAtIndex(0);
						_portNumber = String.valueOf(primaryAdaptor.port());
					}
				}
			}
			return _portNumber != null ? _portNumber : "N/A";
		}
	}

	/**
	 * The <code>AppInfoPatternConverter</code> is useful for logging various
	 * info about the WebObjects application instance. See
	 * {@link ERXPatternLayout} for example/supported partterns.
	 */
	private class AppInfoPatternConverter extends PatternConverter {

		/** The output's template, with @@key@@ placeholders (see {@link ERXPatternParser#fillTemplate}) */
		private String _template;

		/**
		 * The values that don't change during the application's life span (name, pid, port), collected on the first
		 * event after the application exists
		 */
		private Map<String, String> _constants;

		/**
		 * Default package level constructor
		 * 
		 * @param formattingInfo
		 *            current pattern formatting information
		 * @param format
		 *            string for the logging event format
		 */
		// FIXME: Work in progress - fixed template; format parameter will be
		// ignored for now.
		AppInfoPatternConverter(FormattingInfo formattingInfo, String format) {
			super(formattingInfo);
			_template = "@@appName@@[@@pid@@:@@portNumber@@ @@sessionCount@@]";
			if(format != null && format.length() > 0) {
				format = format.replaceFirst("(^|\\W)s(\\W|$)", "$1@@sessionCount@@$2");
				format = format.replaceFirst("(^|\\W)n(\\W|$)", "$1@@appName@@$2");
				format = format.replaceFirst("(^|\\W)p(\\W|$)", "$1@@portNumber@@$2");
				format = format.replaceFirst("(^|\\W)i(\\W|$)", "$1@@pid@@$2");
				_template = format;
			}

		}

		/**
		 * Returns ...
		 * <p>
		 * 
		 * ... has not been created yet then "-" is logged.
		 * 
		 * @param event
		 *            a given logging event
		 * @return the current application name
		 */
		@Override
		public String convert(LoggingEvent event) {
			final WOApplication app = WOApplication.application();
			final Map<String, String> values = new HashMap<>();

			if (app != null) {
				if (_constants == null) {
					_constants = applicationConstants(app);
				}

				values.putAll(_constants);
				values.put("sessionCount", String.valueOf(app.activeSessionsCount()));
			}

			return fillTemplate(_template, values);
		}

		private static Map<String, String> applicationConstants(final WOApplication app) {
			final Map<String, String> constants = new HashMap<>();
			final String pid = System.getProperty("com.webobjects.pid");

			if (pid != null) {
				constants.put("pid", pid);
			}

			if (app.name() != null) {
				constants.put("appName", app.name());
			}

			if (app.port() != null && app.port().intValue() > 0) {
				constants.put("portNumber", app.port().toString());
			}
			else {
				// WO 5.1.x -- Apple Ref# 2260519
				final NSArray adaptors = app.adaptors();

				if (adaptors != null && adaptors.count() > 0) {
					constants.put("portNumber", String.valueOf(((WOAdaptor) adaptors.objectAtIndex(0)).port()));
				}
			}

			return constants;
		}
	}

	/**
	 * The <code>JavaVMInfoPatternConverter</code> is useful for logging various
	 * info about the Java runtime and Virtual Machine that is running the
	 * application instance. See {@link ERXPatternLayout} for example/supported
	 * patterns.
	 */
	private class JavaVMInfoPatternConverter extends PatternConverter {

		/** */
		private Runtime _runtime;


		/** The output's template, with @@key@@ placeholders (see {@link ERXPatternParser#fillTemplate}) */
		private String _template;

		/**
		 * Default package level constructor
		 * 
		 * @param formattingInfo
		 *            current pattern formatting information
		 * @param format
		 *            string for the logging event format
		 */
		// FIXME: Work in progress - fixed template; format parameter will be ignored for now.
		JavaVMInfoPatternConverter(FormattingInfo formattingInfo, String format) {
			super(formattingInfo);
			_runtime = Runtime.getRuntime();
			format = format.replaceFirst("(^|\\W)u(\\W|$)", "$1@@usedMemory@@$2");
			format = format.replaceFirst("(^|\\W)f(\\W|$)", "$1@@freeMemory@@$2");
			format = format.replaceFirst("(^|\\W)t(\\W|$)", "$1@@totalMemory@@$2");
			format = format.replaceFirst("(^|\\W)m(\\W|$)", "$1@@maxMemory@@$2");
			_template = format;
		}

		/**
		 * Returns ...
		 * <p>
		 * 
		 * ... has not been created yet then "-" is logged.
		 * 
		 * @param event
		 *            a given logging event
		 * @return the current application name
		 */
		@Override
		public String convert(LoggingEvent event) {
			long totalMemory = _runtime.totalMemory();
			long freeMemory = _runtime.freeMemory();
			long maxMemory = _runtime.maxMemory();
			long usedMemory = totalMemory - freeMemory;

			final Map<String, String> values = new HashMap<>();
			values.put("totalMemory", ERXUtilities.formatByteCount(totalMemory));
			values.put("freeMemory", ERXUtilities.formatByteCount(freeMemory));
			values.put("usedMemory", ERXUtilities.formatByteCount(usedMemory));
			values.put("maxMemory", ERXUtilities.formatByteCount(maxMemory));

			return fillTemplate(_template, values);
		}
	}


	/**
	 * Placeholders in the templates of the %W and %V conversions: a key between double at-signs
	 */
	private static final Pattern PLACEHOLDER = Pattern.compile("@@(\\w+)@@");

	/**
	 * @return The template with each @@key@@ replaced by the key's value, or by "-" when there's none
	 */
	static String fillTemplate(final String template, final Map<String, String> values) {
		final Matcher matcher = PLACEHOLDER.matcher(template);
		final StringBuilder result = new StringBuilder();

		while (matcher.find()) {
			final String value = values.get(matcher.group(1));
			matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? value : "-"));
		}

		matcher.appendTail(result);
		return result.toString();
	}
}
