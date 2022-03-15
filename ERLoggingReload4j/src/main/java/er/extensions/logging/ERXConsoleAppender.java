package er.extensions.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.regex.Pattern;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSMutableArray;

import er.extensions.foundation.ERXExceptionUtilities;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXThreadStorage;
import er.extensions.foundation.ERXUtilities;
import er.extensions.localization.ERXLocalizer;

/**
 * ERXConsoleAppender is just like ConsoleAppender except that it display stack
 * traces using ERXExceptionUtilities. Additionally, it will not log the same
 * exception twice in a row, preventing the annoying problem where you may log
 * from multiple places in your code and produce multiple copies of the same
 * exception trace.
 * 
 * @author mschrag
 */
public class ERXConsoleAppender extends ConsoleAppender {
	private static final String LAST_THROWABLE_KEY = "er.extensions.logging.ERXConsoleAppender.lastThrowable";

	public ERXConsoleAppender() {
		super();
	}

	@SuppressWarnings("hiding")
	public ERXConsoleAppender(Layout layout) {
		super(layout);
	}

	@SuppressWarnings("hiding")
	public ERXConsoleAppender(Layout layout, String target) {
		super(layout, target);
	}

	@Override
	protected void subAppend(LoggingEvent event) {
		qw.write(super.layout.format(event));

		if (super.layout.ignoresThrowable()) {
			ThrowableInformation throwableInfo = event.getThrowableInformation();
			if (throwableInfo != null) {
				Throwable throwable = throwableInfo.getThrowable();
				Throwable lastThrowable = (Throwable) ERXThreadStorage.valueForKey(ERXConsoleAppender.LAST_THROWABLE_KEY);
				if (throwable != null) {
					if (lastThrowable != throwable) {
						StringWriter exceptionStringWriter = new StringWriter();
						StackTracePrinter.printStackTrace(throwable, new PrintWriter(exceptionStringWriter, true));
						String exceptionStr = exceptionStringWriter.toString();
						if (exceptionStr.length() > 0) {
							for (String line : exceptionStr.split("[\r\n]+")) {
								qw.write(line);
								qw.write(Layout.LINE_SEP);
							}
						}
						ERXThreadStorage.takeValueForKey(throwable, ERXConsoleAppender.LAST_THROWABLE_KEY);
					}
				}
			}
		}

		if (immediateFlush) {
			qw.flush();
		}
	}
	
	private static class StackTracePrinter {
		
		private static final Logger log = LoggerFactory.getLogger( StackTracePrinter.class );

		/**
		 * Prints the given throwable to the given printwriter.
		 * 
		 * @param t the throwable to print
		 * @param writer the writer to print to
		 */
		public static void printStackTrace(Throwable t, PrintWriter writer) {
			printStackTrace(t, writer, 0);
		}

		private static NSArray<Pattern> _skipPatterns;

		private static void _printSingleStackTrace(Throwable t, PrintWriter writer, int exceptionDepth, boolean cleanupStackTrace) {
			NSArray<Pattern> skipPatterns = _skipPatterns;
			if (cleanupStackTrace && skipPatterns == null) {
				String skipPatternsFile = ERXProperties.stringForKey("er.extensions.stackTrace.skipPatternsFile");
				if (skipPatternsFile != null) {
					NSMutableArray<Pattern> mutableSkipPatterns = new NSMutableArray<>();

					Enumeration<String> frameworksEnum = ERXLocalizer.frameworkSearchPath().reverseObjectEnumerator();
					while (frameworksEnum.hasMoreElements()) {
						String framework = frameworksEnum.nextElement();
						URL path = WOApplication.application().resourceManager().pathURLForResourceNamed(skipPatternsFile, framework, null);
						if (path != null) {
							try {
								NSArray<String> skipPatternStrings = (NSArray<String>) ERXUtilities.readPropertyListFromFileInFramework(skipPatternsFile, framework, (NSArray)null, "utf-8");
								if (skipPatternStrings != null) {
									for (String skipPatternString : skipPatternStrings) {
										try {
											mutableSkipPatterns.addObject(Pattern.compile(skipPatternString));
										}
										catch (Throwable patternThrowable) {
											log.error("Skipping invalid exception pattern '{}' in '{}' in the framework '{}' ({})",
													skipPatternString, skipPatternsFile, framework, toParagraph(patternThrowable));
										}
									}
								}
							}
							catch (Throwable patternThrowable) {
								log.error("Failed to read pattern file '{}' in the framework '{}' ({})",
										skipPatternsFile, framework, toParagraph(patternThrowable));
							}
						}
					}

					skipPatterns = mutableSkipPatterns;
				}

				if (ERXProperties.booleanForKeyWithDefault("er.extensions.stackTrace.cachePatterns", true)) {
					if (skipPatterns == null) {
						_skipPatterns = NSArray.EmptyArray;
					}
					else {
						_skipPatterns = skipPatterns;
					}
				}
			}

			StackTraceElement[] elements = t.getStackTrace();

			indent(writer, exceptionDepth);
			if (exceptionDepth > 0) {
				writer.print("Caused by a ");
			}
			if (cleanupStackTrace) {
				writer.print(t.getClass().getSimpleName());
			}
			else {
				writer.print(t.getClass().getName());
			}
			String message = t.getLocalizedMessage();
			if (message != null) {
				writer.print(": ");
				writer.print(message);
			}
			writer.println();

			int stackDepth = 0;
			int skippedCount = 0;
			for (StackTraceElement element : elements) {
				boolean showElement = true;

				if (stackDepth > 0 && cleanupStackTrace && skipPatterns != null && !skipPatterns.isEmpty()) {
					String elementName = element.getClassName() + "." + element.getMethodName();
					for (Pattern skipPattern : skipPatterns) {
						if (skipPattern.matcher(elementName).matches()) {
							showElement = false;
							break;
						}
					}
				}

				if (!showElement) {
					skippedCount++;
				}
				else {
					if (skippedCount > 0) {
						indent(writer, exceptionDepth + 1);
						writer.println("   ... skipped " + skippedCount + " stack elements");
						skippedCount = 0;
					}
					indent(writer, exceptionDepth + 1);
					writer.print("at ");
					writer.print(element.getClassName());
					writer.print(".");
					writer.print(element.getMethodName());
					writer.print("(");
					if (element.isNativeMethod()) {
						writer.print("Native Method");
					}
					else if (element.getLineNumber() < 0) {
						writer.print(element.getFileName());
						writer.print(":Unknown");
					}
					else {
						writer.print(element.getFileName());
						writer.print(":");
						writer.print(element.getLineNumber());
					}
					writer.print(")");
					writer.println();
				}

				stackDepth++;
			}

			if (skippedCount > 0) {
				indent(writer, exceptionDepth + 1);
				writer.println("... skipped " + skippedCount + " stack elements");
			}
		}
		
		private static void indent(PrintWriter writer, int level) {
			for (int i = 0; i < level; i++) {
				writer.append("  ");
			}
		}

		/**
		 * Prints the given throwable to the given writer with an indent.
		 * 
		 * @param t the throwable to print
		 * @param writer the writer to print to
		 * @param exceptionDepth the indent level to use
		 * 
		 * @property er.extensions.stackTrace.cleanup if true, stack traces are cleaned up for easier use
		 * @property er.extensions.stackTrace.skipPatternsFile the name the resource that contains an array of class name and method regexes to skip in stack traces
		 */
		private static void printStackTrace(Throwable t, PrintWriter writer, int exceptionDepth) {
			try {
				boolean cleanupStackTrace = ERXProperties.booleanForKeyWithDefault("er.extensions.stackTrace.cleanup", false);
				Throwable actualThrowable;
				if (cleanupStackTrace) {
					actualThrowable = t;
				}
				else {
					actualThrowable = ERXExceptionUtilities.getMeaningfulThrowable(t);
				}
				if (actualThrowable == null) {
					return;
				}

				Throwable cause = getCause(actualThrowable);
				boolean showOnlyBottomException = ERXProperties.booleanForKeyWithDefault("er.extensions.stackTrace.bottomOnly", true);
				if (!showOnlyBottomException || cause == null) {
					_printSingleStackTrace(actualThrowable, writer, exceptionDepth, cleanupStackTrace);
				}
				if (cause != null && cause != actualThrowable) {
					printStackTrace(cause, writer, exceptionDepth);
				}
			}
			catch (Throwable thisSucks) {
				writer.println("ERXExceptionUtilities.printStackTrace Failed!");
				thisSucks.printStackTrace(writer);
			}
		}
		
		/**
		 * Returns the cause of an exception.  This should be modified to be pluggable.
		 * 
		 * @param t the original exception
		 * @return the cause of the exception or null of there isn't one
		 */
		private static Throwable getCause(Throwable t) {
			Throwable cause = null;
			if (t != null) {
				cause = t.getCause();
				if (cause == null) {
					try {
						// Check for OGNL root causes
						Class ognlExceptionClass = Class.forName("ognl.OgnlException");
						if (ognlExceptionClass.isAssignableFrom(t.getClass())) {
							Method reasonMethod = ognlExceptionClass.getDeclaredMethod("getReason");
							cause = (Throwable) reasonMethod.invoke(t);
						}
					}
					catch (Throwable e) {
						// IGNORE
					}
				}
			}
			if (t == cause) {
				cause = null;
			}
			return cause;
		}

		/**
		 * Returns a paragraph form of the given throwable.
		 * 
		 * @param t the throwable to convert to paragraph form
		 * @return the paragraph string
		 */
		private static String toParagraph(Throwable t) {
			return toParagraph(t, true);
		}

		/**
		 * Returns a paragraph form of the given throwable.
		 * 
		 * @param t the throwable to convert to paragraph form
		 * @param removeHtmlTags if true, html tags will be filtered from the error messages (to remove, for instance, bold tags from validation messages)
		 * @return the paragraph string
		 */
		private static String toParagraph(Throwable t, boolean removeHtmlTags) {
			StringBuilder messageBuffer = new StringBuilder();
			boolean foundInternalError = false;
			Throwable throwable = t;
			while (throwable != null) {
				if (messageBuffer.length() > 0) {
					messageBuffer.append(' ');
				}
				Throwable oldThrowable = ERXExceptionUtilities.getMeaningfulThrowable(throwable);
				String message = throwable.getLocalizedMessage();
				if (message == null) {
					if (!foundInternalError) {
						message = "Your request produced an error.";
						foundInternalError = true;
					}
					else {
						message = "";
					}
				}
				if (removeHtmlTags) {
					message = message.replaceAll("<[^>]+>", "");
				}
				message = message.trim();
				messageBuffer.append(message);
				if (!message.endsWith(".")) {
					messageBuffer.append('.');
				}
				throwable = getCause(oldThrowable);
			}
			return messageBuffer.toString();
		}
	}
}