package com.webobjects.woextensions.error;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSBundle;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSPropertyListSerialization;
import com.webobjects.foundation.development.NSMavenProjectBundle;
import com.webobjects.woextensions.error.WOExceptionPage.WOExceptionParser.WOParsedErrorLine;

import er.extensions.appserver.ERXApplication;
import er.extensions.components.ERXComponent;
import er.extensions.foundation.ERXProperties;
import er.extensions.foundation.ERXThreadStorage;

/**
 * A nicer version of WOExceptionPage.
 * 
 * When in development mode, it will show java code where exception occurred (highlighting the exact line)  
 */

public class WOExceptionPage extends ERXComponent {

	private static final Logger logger = LoggerFactory.getLogger( WOExceptionPage.class );

	private static final int NUMBER_OF_LINES_BEFORE_ERROR_LINE = 7;
	private static final int NUMBER_OF_LINES_AFTER_ERROR_LINE = 7;

	/**
	 * The exception we're reporting.
	 */
	private Throwable _exception;

	/**
	 * Line of source file currently being iterated over in the view.
	 */
	public String currentSourceLine;

	/**
	 * Current index of the source line iteration.
	 */
	public int currentSourceLineIndex;

	/**
	 * WO class that parses the stack trace for us.
	 */
	public WOExceptionParser exceptionParser;

	/**
	 * Line of the stack trace currently being iterated over.
	 */
	public WOParsedErrorLine currentErrorLine;

	/**
	 * A path modifier to put between bundle path and modules so that source code locations outside the
	 * regular path can be accommodated.
	 */
	private String pathModifier;
	
	public WOExceptionPage( WOContext aContext ) {
		super( aContext );
		pathModifier = "";
	}

	/**
	 * @return Current time for display in the exception page
	 */
	public LocalDateTime now() {
		return LocalDateTime.now();
	}

	/**
	 * Set the exception ID for display in the page.
	 *
	 * The value is stored with the current thread. Not optimal, but we don't have a reference to the page instance when it's being generated.
	 */
	public static void setExceptionID( final String exceptionID ) {
		ERXThreadStorage.takeValueForKey( exceptionID, "exceptionID" );
	}
	/**
	 * @return The ID of the exception, as generated in ERXApplication.handleException(). Helps with tracing user error reports.
	 */
	public String exceptionID() {
		return (String) ERXThreadStorage.valueForKey("exceptionID");
	}

	/**
	 * Thread-storage key under which the captured WOContext snapshot is stashed.
	 */
	private static final String CONTEXT_SNAPSHOT_KEY = "WOExceptionPage.contextSnapshot";

	/**
	 * A point-in-time capture of the component hierarchy (the "WOContext stack")
	 * that was rendering when the exception was thrown.
	 *
	 * <p>This must be captured <em>at the moment the exception is handled</em>
	 * — in {@code ERXApplication.handleException()}, where the originating
	 * {@link WOContext} is still live and meaningful — rather than read off the
	 * exception page later. By the time the page renders we're in a fresh
	 * request cycle and the original context is gone. So we snapshot the useful
	 * bits into plain data there and carry it across via {@link ERXThreadStorage},
	 * mirroring how {@link #setExceptionID} works.
	 *
	 * <p>The snapshot is intentionally just strings — no live references to
	 * components or context — so nothing keeps request objects alive and the
	 * display can't trip over half-torn-down state.
	 *
	 * <p><b>Not yet clickable.</b> Mapping each component back to its exact
	 * source position in the template requires the parser's per-element source
	 * ranges to reach the live element tree, which the runtime currently
	 * discards at element-construction time. This snapshot establishes the
	 * display; clickability follows once positions are threaded through.
	 */
	public static class ContextSnapshot {

		/**
		 * The component hierarchy, innermost (the component being rendered at
		 * the throw site) first, walking up to the page. Each entry is a frame.
		 */
		private final List<Frame> _frames;

		/**
		 * The binding that was being resolved when the exception was thrown, if
		 * the exception carried a {@link parsley.ParsleyBindingLocation} — the
		 * element attribute name (e.g. {@code value}) and the key path (e.g.
		 * {@code propValue}). Null when no binding location was attached.
		 */
		private final String _failingBindingName;
		private final String _failingBindingKeyPath;

		/**
		 * The request-handling phase the exception occurred in — one of the
		 * three WO lifecycle phases ("append to response", "take values from
		 * request", "invoke action") — or null if it couldn't be determined from
		 * the stack.
		 */
		private final String _requestPhase;

		public ContextSnapshot( final List<Frame> frames, final String failingBindingName, final String failingBindingKeyPath, final String requestPhase ) {
			_frames = frames;
			_failingBindingName = failingBindingName;
			_failingBindingKeyPath = failingBindingKeyPath;
			_requestPhase = requestPhase;
		}

		public List<Frame> frames() {
			return _frames;
		}

		/** @return a human-readable request phase label, or null if unknown. */
		public String requestPhase() {
			return _requestPhase;
		}

		/** @return the failing binding's attribute name, or null if unknown. */
		public String failingBindingName() {
			return _failingBindingName;
		}

		/** @return the failing binding's key path, or null if unknown. */
		public String failingBindingKeyPath() {
			return _failingBindingKeyPath;
		}

		/** @return true if a failing binding was captured. */
		public boolean hasFailingBinding() {
			return _failingBindingKeyPath != null || _failingBindingName != null;
		}

		public boolean hasFrames() {
			return _frames != null && !_frames.isEmpty();
		}

		/**
		 * One component in the captured hierarchy.
		 */
		public static class Frame {
			/**
			 * The component's simple name (e.g. {@code Main}), derived from
			 * {@code WOComponent.name()} with any package prefix stripped — not
			 * from the Java class, which is {@code WOComponent} for every
			 * classless component. Unique within an application, so it's the
			 * only identifier we need, and it doubles as the key the dev server
			 * resolves to open the component.
			 */
			private final String _componentName;
			private final boolean _isPage;
			private final boolean _isFailingComponent;

			/**
			 * The template file path for this frame, if it could be resolved.
			 * Currently populated only for the failing frame, from the source
			 * location captured by ParsleyProxyElement. Null otherwise.
			 */
			private String _templateFile;

			/**
			 * The 1-based line in {@link #_templateFile} where the failing
			 * element sits, or 0 if unknown.
			 */
			private int _templateLine;

			/**
			 * The full text of the template file, captured at resolution time so
			 * the exception page can render a source preview without re-reading
			 * the file. Null unless a template location was resolved.
			 */
			private String _templateText;

			public Frame( final String componentName, final boolean isPage, final boolean isFailingComponent ) {
				_componentName = componentName;
				_isPage = isPage;
				_isFailingComponent = isFailingComponent;
			}

			/** @return the component's simple class name, unique within the app. */
			public String componentName() {
				return _componentName;
			}

			public boolean isPage() {
				return _isPage;
			}

			/** True for the innermost component — the one rendering at the throw site. */
			public boolean isFailingComponent() {
				return _isFailingComponent;
			}

			void setTemplateLocation( final String templateFile, final int templateLine, final String templateText ) {
				_templateFile = templateFile;
				_templateLine = templateLine;
				_templateText = templateText;
			}

			/** @return the full template text, or null if not resolved. */
			public String templateText() {
				return _templateText;
			}

			/** @return the resolved template file path, or null if not resolved. */
			public String templateFile() {
				return _templateFile;
			}

			/** @return the 1-based template line, or 0 if unknown. */
			public int templateLine() {
				return _templateLine;
			}

			/** @return true if this frame has a resolved template file + line. */
			public boolean hasTemplateLocation() {
				return _templateFile != null && _templateLine > 0;
			}

			/** @return just the file name of the template (no directory), for display. */
			public String templateFileName() {
				if( _templateFile == null ) {
					return null;
				}
				final int slash = _templateFile.lastIndexOf( '/' );
				return slash == -1 ? _templateFile : _templateFile.substring( slash + 1 );
			}
		}
	}

	/**
	 * Captures the component hierarchy from a live {@link WOContext} into a
	 * plain-data {@link ContextSnapshot} and stashes it for the exception page
	 * to display.
	 *
	 * <p>Call this from {@code ERXApplication.handleException()}, where the
	 * originating context is still available. Safe to call with a null context
	 * or one without a component (stashes nothing).
	 *
	 * <p>If the thrown exception carries a {@link parsley.ParsleySourceLocation}
	 * (attached by {@code ParsleyProxyElement} as a suppressed throwable), the
	 * failing frame is also resolved to a template file + line.
	 *
	 * @param throwable the exception that was thrown (may carry a source location)
	 * @param context   the context that was rendering when the exception was thrown
	 */
	public static void setContextSnapshot( final Throwable throwable, final WOContext context ) {
		if( context == null || context.component() == null ) {
			return;
		}

		final List<ContextSnapshot.Frame> frames = new ArrayList<>();

		final com.webobjects.appserver.WOComponent failingComponent = context.component();
		com.webobjects.appserver.WOComponent component = failingComponent;
		final com.webobjects.appserver.WOComponent page = context.page();

		ContextSnapshot.Frame failingFrame = null;

		// Defensive depth cap — a malformed parent chain must not hang error reporting.
		int guard = 0;
		boolean innermost = true;

		while( component != null && guard < 500 ) {
			final boolean isPage = component == page;
			final ContextSnapshot.Frame frame = new ContextSnapshot.Frame(
					simpleComponentName( component ),
					isPage,
					innermost );
			frames.add( frame );

			if( innermost ) {
				failingFrame = frame;
			}

			if( isPage ) {
				break;
			}

			component = component.parent();
			innermost = false;
			guard++;
		}

		// If the exception carries a Parsley source location, resolve the
		// failing element's template file + line and attach it to the failing
		// frame. Wrapped in a catch-all: source resolution must never break the
		// error-handling path.
		try {
			resolveTemplateLocation( throwable, failingComponent, failingFrame );
		}
		catch( final Throwable t ) {
			// Best-effort only.
		}

		// If the exception carries a Parsley binding location (attached by
		// ParsleyKeyValueAssociation when a binding resolution threw), capture
		// which binding was being pulled. Best-effort.
		String bindingName = null;
		String bindingKeyPath = null;
		try {
			final parsley.ParsleyBindingLocation bindingLocation = findBindingLocation( throwable );
			if( bindingLocation != null ) {
				bindingName = bindingLocation.bindingName();
				bindingKeyPath = bindingLocation.keyPath();
			}
		}
		catch( final Throwable t ) {
			// Best-effort only.
		}

		// Determine which request-handling phase we were in (append / take
		// values / invoke action) from the exception's stack. Best-effort.
		String requestPhase = null;
		try {
			requestPhase = requestPhaseFromThrowable( throwable );
		}
		catch( final Throwable t ) {
			// Best-effort only.
		}

		ERXThreadStorage.takeValueForKey( new ContextSnapshot( frames, bindingName, bindingKeyPath, requestPhase ), CONTEXT_SNAPSHOT_KEY );
	}

	/**
	 * The three WO request-handling phases, keyed by the lifecycle method that
	 * drives each. We scan the exception's stack for these method names to label
	 * which phase the failure happened in.
	 */
	private static final String PHASE_METHOD_APPEND = "appendToResponse";
	private static final String PHASE_METHOD_TAKE_VALUES = "takeValuesFromRequest";
	private static final String PHASE_METHOD_INVOKE_ACTION = "invokeAction";

	/**
	 * Determines the request-handling phase by scanning the throwable (and its
	 * cause chain) for the WO lifecycle method that drives each phase:
	 * {@code appendToResponse}, {@code takeValuesFromRequest}, or
	 * {@code invokeAction}. These are the canonical WO request-lifecycle entry
	 * points and appear on the stack of any render/action exception.
	 *
	 * <p>The first match wins as we walk from the throw site outward (the stack
	 * is ordered innermost frame first), so a nested phase isn't mistaken for an
	 * outer one.
	 *
	 * @return a human-readable phase label, or null if none was found
	 */
	private static String requestPhaseFromThrowable( final Throwable throwable ) {
		Throwable t = throwable;
		int guard = 0;

		while( t != null && guard < 50 ) {
			for( final StackTraceElement element : t.getStackTrace() ) {
				final String method = element.getMethodName();

				if( PHASE_METHOD_APPEND.equals( method ) ) {
					return "append to response";
				}
				if( PHASE_METHOD_TAKE_VALUES.equals( method ) ) {
					return "take values from request";
				}
				if( PHASE_METHOD_INVOKE_ACTION.equals( method ) ) {
					return "invoke action";
				}
			}

			t = t.getCause();
			guard++;
		}

		return null;
	}

	/**
	 * Derives a component's simple name for display and for the dev-server
	 * "open component" link.
	 *
	 * <p>We use {@link com.webobjects.appserver.WOComponent#name()} rather than
	 * {@code getClass().getSimpleName()} because of <em>classless</em>
	 * components: a template with no Java subclass is instantiated as a plain
	 * {@code WOComponent}, so {@code getSimpleName()} would return
	 * {@code "WOComponent"} for every such component — the real identity only
	 * lives in {@code name()}. We then strip any package prefix, since
	 * {@code name()} may be fully qualified, leaving just the simple name (which
	 * is unique within an application).
	 */
	private static String simpleComponentName( final com.webobjects.appserver.WOComponent component ) {
		String name = component.name();

		if( name == null ) {
			// Fall back to the class simple name if name() is somehow absent.
			return component.getClass().getSimpleName();
		}

		final int lastDot = name.lastIndexOf( '.' );
		if( lastDot != -1 ) {
			name = name.substring( lastDot + 1 );
		}

		return name;
	}

	/**
	 * Searches a throwable and its cause chain for a Parsley source location
	 * attached as a suppressed throwable.
	 *
	 * @return the first source location found, or null
	 */
	private static parsley.ParsleySourceLocation findSourceLocation( final Throwable throwable ) {
		Throwable t = throwable;
		int guard = 0;
		while( t != null && guard < 50 ) {
			final parsley.ParsleySourceLocation location = parsley.ParsleySourceLocation.attachedTo( t );
			if( location != null ) {
				return location;
			}
			t = t.getCause();
			guard++;
		}
		return null;
	}

	/**
	 * Searches a throwable and its cause chain for a Parsley binding location
	 * attached as a suppressed throwable.
	 *
	 * @return the first binding location found, or null
	 */
	private static parsley.ParsleyBindingLocation findBindingLocation( final Throwable throwable ) {
		Throwable t = throwable;
		int guard = 0;
		while( t != null && guard < 50 ) {
			final parsley.ParsleyBindingLocation location = parsley.ParsleyBindingLocation.attachedTo( t );
			if( location != null ) {
				return location;
			}
			t = t.getCause();
			guard++;
		}
		return null;
	}

	/**
	 * Resolves the failing element's template file and line from the
	 * {@link parsley.ParsleySourceLocation} (if any) attached to the exception,
	 * and records it on the failing frame.
	 *
	 * <p>The source location gives a character offset into the template string
	 * of the failing component. We find the component's template file via the
	 * (deprecated but still working) {@code pathURL()} backdoor — WebObjects
	 * does not otherwise expose a template's path — read the file, and convert
	 * the offset to a 1-based line number.
	 */
	private static void resolveTemplateLocation( final Throwable throwable, final com.webobjects.appserver.WOComponent failingComponent, final ContextSnapshot.Frame failingFrame ) {
		if( throwable == null || failingFrame == null || failingComponent == null ) {
			return;
		}

		// The source location is attached (as a suppressed throwable) to
		// whichever exception object was in flight when it passed through
		// ParsleyProxyElement. By the time we see it here it may be wrapped, so
		// search the throwable and its cause chain.
		final parsley.ParsleySourceLocation location = findSourceLocation( throwable );
		if( location == null || location.sourceRange() == null ) {
			return;
		}

		final int offset = location.sourceRange().start();

		// Resolve the component's template file. pathURL() returns the .wo
		// folder URL (e.g. .../Main.wo); the html template inside is named after
		// the folder (Main.wo -> Main.html). We derive the name from the folder
		// rather than the Java class, because a classless component's class is
		// just WOComponent — getSimpleName() would point us at WOComponent.html.
		final java.net.URL pathURL = failingComponent.pathURL();
		if( pathURL == null ) {
			return;
		}

		String folderPath = pathURL.getPath();
		// Trim a trailing slash so the last path segment is the .wo folder name.
		if( folderPath.endsWith( "/" ) ) {
			folderPath = folderPath.substring( 0, folderPath.length() - 1 );
		}

		final int lastSlash = folderPath.lastIndexOf( '/' );
		String folderName = lastSlash == -1 ? folderPath : folderPath.substring( lastSlash + 1 );
		if( folderName.endsWith( ".wo" ) ) {
			folderName = folderName.substring( 0, folderName.length() - ".wo".length() );
		}

		final String templateFilePath = folderPath + "/" + folderName + ".html";

		final Path templatePath = Paths.get( templateFilePath );
		if( !Files.exists( templatePath ) ) {
			return;
		}

		final String templateText;
		try {
			templateText = Files.readString( templatePath );
		}
		catch( final IOException e ) {
			return;
		}

		final int line = lineNumberForOffset( templateText, offset );
		failingFrame.setTemplateLocation( templateFilePath, line, templateText );
	}

	/**
	 * Converts a character offset into a 1-based line number by counting the
	 * newlines that precede it.
	 *
	 * @param text   the source text
	 * @param offset a character offset into {@code text}
	 * @return the 1-based line number containing {@code offset}
	 */
	private static int lineNumberForOffset( final String text, final int offset ) {
		final int cappedOffset = Math.min( offset, text.length() );
		int line = 1;
		for( int i = 0; i < cappedOffset; i++ ) {
			if( text.charAt( i ) == '\n' ) {
				line++;
			}
		}
		return line;
	}

	/**
	 * @return The captured WOContext snapshot for the current exception, or
	 *         null if none was captured.
	 */
	public ContextSnapshot contextSnapshot() {
		return (ContextSnapshot) ERXThreadStorage.valueForKey( CONTEXT_SNAPSHOT_KEY );
	}

	/**
	 * @return true if there's a component hierarchy to show. Dev-mode only —
	 *         like the source previews, the component internals are diagnostic
	 *         detail we don't surface on a production error page.
	 */
	public boolean showContextStack() {
		if( !ERXApplication.isDevelopmentModeSafe() ) {
			return false;
		}
		final ContextSnapshot snapshot = contextSnapshot();
		return snapshot != null && snapshot.hasFrames();
	}

	/**
	 * @return true if the diagnosis block (component hierarchy + HTML source +
	 *         context rail) has anything to show — so the whole two-column
	 *         layout can be omitted entirely (rather than rendering empty
	 *         wrapper divs) in production, where all of it is gated off.
	 */
	public boolean showDiagnosis() {
		return showContextStack() || showTemplateSource() || showRequestPhase() || showFailingBinding();
	}

	/**
	 * @return The request-handling phase the exception occurred in ("append to
	 *         response", "take values from request", "invoke action"), or null
	 *         if it couldn't be determined.
	 */
	public String requestPhase() {
		final ContextSnapshot snapshot = contextSnapshot();
		return snapshot == null ? null : snapshot.requestPhase();
	}

	/**
	 * @return true if a request phase was determined (so the page can show it).
	 *         Dev-mode only, matching the rest of the diagnostic detail.
	 */
	public boolean showRequestPhase() {
		return ERXApplication.isDevelopmentModeSafe() && requestPhase() != null;
	}

	/**
	 * @return The captured component frames for display, innermost first.
	 */
	public List<ContextSnapshot.Frame> contextFrames() {
		final ContextSnapshot snapshot = contextSnapshot();
		return snapshot == null ? Collections.emptyList() : snapshot.frames();
	}

	/**
	 * @return The captured component frames ordered <em>top-down</em> — the page
	 *         first, the failing component last — for rendering the containment
	 *         hierarchy as an indented tree (each level nested one step deeper
	 *         than its parent). The underlying snapshot is innermost-first, so
	 *         this is simply the reverse.
	 */
	public List<ContextSnapshot.Frame> contextFramesTopDown() {
		final List<ContextSnapshot.Frame> frames = new ArrayList<>( contextFrames() );
		Collections.reverse( frames );
		return frames;
	}

	/**
	 * @return true if we captured which binding was being resolved when the
	 *         exception was thrown (only available when the failure happened
	 *         inside a Parsley binding resolution).
	 */
	public boolean showFailingBinding() {
		if( !ERXApplication.isDevelopmentModeSafe() ) {
			return false;
		}
		final ContextSnapshot snapshot = contextSnapshot();
		return snapshot != null && snapshot.hasFailingBinding();
	}

	/**
	 * @return A human-readable description of the failing binding, e.g.
	 *         {@code value="$propValue"} — the attribute and the key path it was
	 *         resolving. Falls back to just the key path or just the name if only
	 *         one is known. Null if no binding was captured.
	 */
	public String failingBindingDescription() {
		final ContextSnapshot snapshot = contextSnapshot();

		if( snapshot == null || !snapshot.hasFailingBinding() ) {
			return null;
		}

		final String name = snapshot.failingBindingName();
		final String keyPath = snapshot.failingBindingKeyPath();

		// Both known: render as the inline binding the developer wrote.
		if( name != null && keyPath != null ) {
			return "%s=\"$%s\"".formatted( name, keyPath );
		}

		// Only the key path: show it with the $ the developer would recognise.
		if( keyPath != null ) {
			return "$" + keyPath;
		}

		// Only the binding name.
		return name;
	}

	// ---------------------------------------------------------------------
	// Template source preview
	//
	// The component-stack counterpart of the Java source preview: when the
	// failing component's template location was resolved (via the source
	// position Parsley attached to the exception), we show a window of the
	// template source around the failing line — highlighted and clickable to
	// open the template at that line in the IDE — exactly mirroring the Java
	// preview's look and behaviour.
	// ---------------------------------------------------------------------

	/**
	 * @return The failing frame (innermost component) if it carries a resolved
	 *         template location, otherwise null.
	 */
	private ContextSnapshot.Frame failingFrameWithTemplate() {
		final ContextSnapshot snapshot = contextSnapshot();

		if( snapshot == null ) {
			return null;
		}

		for( final ContextSnapshot.Frame frame : snapshot.frames() ) {
			if( frame.isFailingComponent() && frame.hasTemplateLocation() && frame.templateText() != null ) {
				return frame;
			}
		}

		return null;
	}

	/**
	 * @return true if there's a resolved template source to preview (dev mode
	 *         only — there's no IDE server to click into otherwise).
	 */
	public boolean showTemplateSource() {
		return ERXApplication.isDevelopmentModeSafe() && failingFrameWithTemplate() != null;
	}

	/**
	 * @return The file name of the previewed template (no directory), for the
	 *         preview's filename header.
	 */
	public String templateSourceFileName() {
		final ContextSnapshot.Frame frame = failingFrameWithTemplate();
		return frame == null ? null : frame.templateFileName();
	}

	/**
	 * The 1-based index (into {@link #templateLines()}) of the first template
	 * line shown in the preview window. Used to map iteration index → actual
	 * line number and to find the highlighted line.
	 */
	private int templateFirstShownLine() {
		final ContextSnapshot.Frame frame = failingFrameWithTemplate();

		if( frame == null ) {
			return 1;
		}

		int first = frame.templateLine() - NUMBER_OF_LINES_BEFORE_ERROR_LINE;
		return first < 1 ? 1 : first;
	}

	/**
	 * @return The window of template source lines to show in the preview —
	 *         a few lines either side of the failing line.
	 */
	public List<String> templateLines() {
		final ContextSnapshot.Frame frame = failingFrameWithTemplate();

		if( frame == null ) {
			return Collections.emptyList();
		}

		// Split on \n keeping the lines; trailing empty lines are fine to drop.
		final List<String> allLines = Arrays.asList( frame.templateText().split( "\n", -1 ) );

		int firstIndex = frame.templateLine() - NUMBER_OF_LINES_BEFORE_ERROR_LINE - 1; // 0-based
		int lastIndex = frame.templateLine() + NUMBER_OF_LINES_AFTER_ERROR_LINE - 1; // 0-based, exclusive end below

		if( firstIndex < 0 ) {
			firstIndex = 0;
		}

		if( lastIndex > allLines.size() ) {
			lastIndex = allLines.size();
		}

		if( firstIndex > lastIndex ) {
			return Collections.emptyList();
		}

		return allLines.subList( firstIndex, lastIndex );
	}

	/**
	 * Iteration variable: the current template source line being rendered.
	 */
	public String currentTemplateLine;

	/**
	 * Iteration index for the template source preview.
	 */
	public int currentTemplateLineIndex;

	/**
	 * @return The actual 1-based line number of the template line currently
	 *         being iterated over.
	 */
	public int currentTemplateActualLineNumber() {
		return templateFirstShownLine() + currentTemplateLineIndex;
	}

	/**
	 * @return true if the current template line is the failing line.
	 */
	private boolean isTemplateLineContainingError() {
		final ContextSnapshot.Frame frame = failingFrameWithTemplate();
		return frame != null && currentTemplateActualLineNumber() == frame.templateLine();
	}

	/**
	 * @return CSS class for the current template source line — highlights the
	 *         failing line and marks lines clickable (they open the template at
	 *         that line in the IDE).
	 */
	public String templateLineClass() {
		final List<String> cssClasses = new ArrayList<>();
		cssClasses.add( "src-line" );

		if( isTemplateLineContainingError() ) {
			cssClasses.add( "error-line" );
		}

		if( currentTemplateLineOnClick() != null ) {
			cssClasses.add( "clickable" );
		}

		return String.join( " ", cssClasses );
	}

	/**
	 * Builds the dev-server URL to open a component in the IDE via the
	 * {@code /openComponent} handler, optionally at a specific line.
	 *
	 * <p>The dev server resolves the component by its simple type name (the same
	 * name {@code OpenComponentAction} matches), which is exactly what a frame
	 * holds in {@link ContextSnapshot.Frame#componentName()}.
	 *
	 * @param componentName the component's simple name
	 * @param lineNumber    a 1-based line to reveal, or {@code <= 0} to just open
	 *                      the component without navigating to a line
	 * @return the bare dev-server URL, or null outside dev mode / for a missing name
	 */
	private String openComponentURL( final String componentName, final int lineNumber ) {
		if( !ERXApplication.isDevelopmentModeSafe() ) {
			return null;
		}

		if( componentName == null || componentName.isEmpty() ) {
			return null;
		}

		final int wolipsPortnumber = ERXProperties.intForKeyWithDefault( "wolips.port", 9485 );
		final String applicationName = application().name();

		final StringBuilder url = new StringBuilder( "http://localhost:" )
				.append( wolipsPortnumber )
				.append( "/openComponent?app=" ).append( applicationName )
				.append( "&component=" ).append( componentName );

		// Only send a line when we actually have one (the failing frame). The
		// handler treats a missing/non-positive line as "just open the component".
		if( lineNumber > 0 ) {
			url.append( "&lineNumber=" ).append( lineNumber );
		}

		// Classic-WOLips compatibility: include the password only if configured.
		// The Parsley dev server ignores it.
		final String wolipsPassword = ERXProperties.stringForKey( "wolips.password" );
		if( wolipsPassword != null ) {
			url.append( "&pw=" ).append( wolipsPassword );
		}

		return url.toString();
	}

	/**
	 * Builds the dev-server URL to open the previewed template at a given line.
	 *
	 * @param lineNumber the 1-based line to open
	 * @return the bare dev-server URL, or null if not navigable
	 */
	private String openTemplateURLForLine( int lineNumber ) {
		final ContextSnapshot.Frame frame = failingFrameWithTemplate();

		if( frame == null ) {
			return null;
		}

		return openComponentURL( frame.componentName(), lineNumber );
	}

	/**
	 * @return A JavaScript {@code onclick} snippet for the current template line,
	 *         opening the template at that line in the IDE; null outside dev mode.
	 */
	public String currentTemplateLineOnClick() {
		final String url = openTemplateURLForLine( currentTemplateActualLineNumber() );

		if( url == null ) {
			return null;
		}

		return "invokeURL('%s')".formatted( url );
	}

	/**
	 * Iteration variable for rendering the component stack in the template.
	 */
	public ContextSnapshot.Frame currentContextFrame;

	/**
	 * Iteration index for the top-down hierarchy render. The list is page-first,
	 * so index 0 is the page and the last index is the failing component.
	 */
	public int currentContextFrameIndex;

	/**
	 * @return An inline {@code style} value indenting the current hierarchy row.
	 *
	 *         <p>The indentation is <em>inverted</em>: the page (outermost
	 *         container, rendered first) gets the most indent and the failing
	 *         component (innermost, where the throw happened) gets the least —
	 *         so the eye is drawn down-and-left to the component that actually
	 *         failed, which sits flush at the bottom.
	 */
	public String currentContextFrameIndentStyle() {
		final int count = contextFramesTopDown().size();
		// Invert: page (index 0) gets the deepest indent, failing component
		// (last index) gets zero. 18px per level.
		final int level = count - 1 - currentContextFrameIndex;
		return "margin-left: %dpx".formatted( level * 18 );
	}

	/**
	 * @return CSS class for the current component-stack row. Adds
	 *         {@code clickable} when the row can be opened in the IDE (cursor +
	 *         hover affordance), matching the presence of its onclick handler.
	 */
	public String currentContextFrameClass() {
		final String categoryClass;

		if( currentContextFrame.isFailingComponent() ) {
			categoryClass = "ctx-failing";
		}
		else if( currentContextFrame.isPage() ) {
			categoryClass = "ctx-page";
		}
		else {
			categoryClass = "";
		}

		if( currentContextFrameOnClick() != null ) {
			return categoryClass.isEmpty() ? "clickable" : categoryClass + " clickable";
		}

		return categoryClass.isEmpty() ? null : categoryClass;
	}

	/**
	 * @return A JavaScript {@code onclick} snippet that opens the current
	 *         component-stack row's component in the IDE — at its failing line
	 *         when it has one (the failing frame), otherwise just opening the
	 *         component. {@code null} outside dev mode, so non-dev pages get no
	 *         click handler. Wired onto the whole row for consistency with the
	 *         Java stack and the template/source previews.
	 */
	public String currentContextFrameOnClick() {
		// Use the failing frame's resolved line when this row is that frame;
		// other rows open the component without a line.
		final int lineNumber = currentContextFrame.hasTemplateLocation() ? currentContextFrame.templateLine() : -1;

		final String url = openComponentURL( currentContextFrame.componentName(), lineNumber );

		if( url == null ) {
			return null;
		}

		return "invokeURL('%s')".formatted( url );
	}

	/**
	 * Specifying a path fragment here will insert that between bundle path and source module path. Example
	 * modifier: "/../.." to go two directories up. May be needed for non-standard workspace setups.
	 * 
	 * @param modifier modifier to insert, should start with a "/"
	 * 
	 */
	public void setPathModifier( String modifier ) {
		pathModifier = modifier;
	}
	
	
	/**
	 * @return First line of the stack trace, essentially the causing line.
	 */
	public WOParsedErrorLine firstLineOfTrace() {
		List<WOParsedErrorLine> stackTrace = exceptionParser.stackTrace();

		if( stackTrace.isEmpty() ) {
			return null;
		}

		return stackTrace.get( 0 );
	}

	/**
	 * The category a stack frame falls into, from the developer's point of
	 * view: is this code I wrote, code elsewhere in my workspace, or someone
	 * else's library?
	 *
	 * The distinction drives the visual treatment of the trace — the whole
	 * point being to let you find <em>the frames you can act on</em> at a
	 * glance, instead of reading every line.
	 */
	public enum FrameCategory {
		/** The application's own main bundle — almost always where you look first. */
		MAIN,
		/** Another project in the workspace (an NSMavenProjectBundle). Source is available. */
		WORKSPACE,
		/** Framework / library code, living in a jar. Context, not something you edit. */
		EXTERNAL
	}

	/**
	 * @return The {@link FrameCategory} for a given parsed stack-trace line.
	 */
	private FrameCategory categoryForLine( WOParsedErrorLine line ) {
		final NSBundle bundle = bundleForClassName( line.packageClassPath() );

		if( bundle != null && NSBundle.mainBundle().equals( bundle ) ) {
			return FrameCategory.MAIN;
		}

		if( bundle instanceof NSMavenProjectBundle ) {
			return FrameCategory.WORKSPACE;
		}

		return FrameCategory.EXTERNAL;
	}

	/**
	 * @return The category of the stack frame currently being iterated over.
	 */
	public FrameCategory currentFrameCategory() {
		return categoryForLine( currentErrorLine );
	}

	/**
	 * @return true if the frame currently being iterated over is in the
	 *         developer's workspace (their app, or another workspace project)
	 *         — i.e. a frame whose source they can open and act on.
	 */
	public boolean currentFrameIsActionable() {
		return currentFrameCategory() != FrameCategory.EXTERNAL;
	}

	/**
	 * An emoji cue for the current frame, surfacing at a glance which frames
	 * you can act on:
	 * <ul>
	 *   <li>🟢 — your application's own code (look here first)</li>
	 *   <li>📄 — another project in your workspace (source available)</li>
	 *   <li>(blank) — framework / library code</li>
	 * </ul>
	 *
	 * @return the emoji, or an empty string for external frames
	 */
	public String currentFrameEmoji() {
		switch( currentFrameCategory() ) {
			case MAIN:
				return "🟢";
			case WORKSPACE:
				return "📄";
			default:
				return "";
		}
	}

	/**
	 * The first stack frame that lives in the developer's workspace (their app
	 * or another workspace project). This is the frame whose source you
	 * actually want to see — the literal first line of a trace is often deep
	 * in framework code where the exception was technically raised, not where
	 * the developer can do anything about it.
	 *
	 * @return the first workspace frame, or {@link #firstLineOfTrace()} if the
	 *         entire trace is in framework code
	 */
	public WOParsedErrorLine firstWorkspaceLineOfTrace() {
		for( WOParsedErrorLine line : exceptionParser.stackTrace() ) {
			if( categoryForLine( line ) != FrameCategory.EXTERNAL ) {
				return line;
			}
		}

		// Entire trace is framework code — fall back to the literal first line
		// so the source preview still shows something.
		return firstLineOfTrace();
	}

	/**
	 * @return true if source should be shown.
	 */
	public boolean showSource() {
		return ERXApplication.isDevelopmentModeSafe() && sourceFileContainingError() != null && !sourceFileContainingError().toString().contains( ".jar/" );
	}

	/**
	 * @return The source file to show in the preview — the first frame in the
	 *         developer's workspace, which is the code they can actually act on
	 *         (not necessarily the literal first line of the trace, which is
	 *         often framework code).
	 */
	private Path sourceFileContainingError() {
		String fullyClassifiedNameOfThrowingClass = firstWorkspaceLineOfTrace().packageClassPath();
		final NSBundle bundle = bundleForClassName( fullyClassifiedNameOfThrowingClass );

		if( bundle == null ) {
			return null;
		}

		// If the exception occurs in an inner class, chop the inner class name off the end to get at the name of the containing class 
		int indexOfInnerClassSeparator = fullyClassifiedNameOfThrowingClass.indexOf("$");

		if( indexOfInnerClassSeparator != -1 ) {
			fullyClassifiedNameOfThrowingClass = fullyClassifiedNameOfThrowingClass.substring(0,indexOfInnerClassSeparator);
		}

		final String pathToJavaFileInProject = fullyClassifiedNameOfThrowingClass.replace( ".", "/" ) + ".java";

		final String pathString;

		if( NSBundle.mainBundle() instanceof NSMavenProjectBundle ) {
			pathString = bundle.bundlePath() + pathModifier + "/src/main/java/" + pathToJavaFileInProject;
		}
		else {
			pathString = bundle.bundlePath() + pathModifier + "/Sources/" + pathToJavaFileInProject;
		}

		return Paths.get( pathString );
	}

	/**
	 * @return The source lines to view in the browser.
	 */
	public List<String> lines() {
		final List<String> lines;

		try {
			lines = Files.readAllLines( sourceFileContainingError() );
		}
		catch( IOException e ) {
			logger.error( "Attempt to read source code from '{}' failed", sourceFileContainingError(), e );
			return new ArrayList<>();
		}

		int indexOfFirstLineToShow = firstWorkspaceLineOfTrace().line() - NUMBER_OF_LINES_BEFORE_ERROR_LINE;
		int indexOfLastLineToShow = firstWorkspaceLineOfTrace().line() + NUMBER_OF_LINES_AFTER_ERROR_LINE;

		if( indexOfFirstLineToShow < 0 ) {
			indexOfFirstLineToShow = 0;
		}

		if( indexOfLastLineToShow > lines.size() ) {
			indexOfLastLineToShow = lines.size();
		}

		return lines.subList( indexOfFirstLineToShow, indexOfLastLineToShow );
	}

	/**
	 * @return Actual number of source file line being iterated over in the view.
	 */
	public int currentActualLineNumber() {
		return firstWorkspaceLineOfTrace().line() - NUMBER_OF_LINES_BEFORE_ERROR_LINE + currentSourceLineIndex + 1;
	}

	/**
	 * Builds an "open in IDE" URL for an arbitrary line of the previewed source
	 * file. The preview always shows the file of {@link #firstWorkspaceLineOfTrace()},
	 * so the class is that frame's class and the line is the one supplied.
	 *
	 * @param lineNumber the 1-based line number to open
	 * @return the bare dev-server URL, or {@code null} if not in dev mode
	 */
	private String openInIDEURLForSourceLine( int lineNumber ) {
		if( !ERXApplication.isDevelopmentModeSafe() ) {
			return null;
		}

		final WOParsedErrorLine frame = firstWorkspaceLineOfTrace();

		if( frame == null ) {
			return null;
		}

		final int wolipsPortnumber = ERXProperties.intForKeyWithDefault( "wolips.port", 9485 );
		final String applicationName = application().name();
		final String className = frame.packageName() + "." + frame.className();

		final StringBuilder url = new StringBuilder( "http://localhost:" )
				.append( wolipsPortnumber )
				.append( "/openJavaFile?app=" ).append( applicationName )
				.append( "&className=" ).append( className )
				.append( "&lineNumber=" ).append( lineNumber );

		final String wolipsPassword = ERXProperties.stringForKey( "wolips.password" );
		if( wolipsPassword != null ) {
			url.append( "&pw=" ).append( wolipsPassword );
		}

		return url.toString();
	}

	/**
	 * @return A JavaScript {@code onclick} snippet for the current line of the
	 *         source preview, so clicking any previewed line opens that line in
	 *         the IDE. {@code null} outside dev mode (no handler is wired).
	 */
	public String currentSourceLineOnClick() {
		final String url = openInIDEURLForSourceLine( currentActualLineNumber() );

		if( url == null ) {
			return null;
		}

		return "invokeURL('%s')".formatted( url );
	}

	/**
	 * @return A JavaScript {@code onclick} snippet for the Java source filename
	 *         header, opening the file at the error line — so the filename is a
	 *         click target like every previewed line and every component row.
	 *         {@code null} outside dev mode.
	 */
	public String javaFilenameOnClick() {
		final WOParsedErrorLine frame = firstWorkspaceLineOfTrace();

		if( frame == null ) {
			return null;
		}

		final String url = openInIDEURLForSourceLine( frame.line() );

		if( url == null ) {
			return null;
		}

		return "invokeURL('%s')".formatted( url );
	}

	/**
	 * @return CSS class for the current line of the source file (highlights the
	 *         error line, and marks lines clickable when they can be opened in
	 *         the IDE).
	 */
	public String sourceLineClass() {
		List<String> cssClasses = new ArrayList<>();
		cssClasses.add( "src-line" );

		if( currentSourceLineIndex % 2 == 0 ) {
			cssClasses.add( "even-line" );
		}
		else {
			cssClasses.add( "odd-line" );
		}

		if( isLineContainingError() ) {
			cssClasses.add( "error-line" );
		}

		// When the line can be opened in the IDE, mark it clickable (cursor +
		// hover) — matching the presence of the line's onclick handler.
		if( currentSourceLineOnClick() != null ) {
			cssClasses.add( "clickable" );
		}

		return String.join( " ", cssClasses );
	}

	/**
	 * @return true if the current line being iterated over is the line containining the error.
	 */
	private boolean isLineContainingError() {
		return currentSourceLineIndex == NUMBER_OF_LINES_BEFORE_ERROR_LINE - 1;
	}

	public Throwable exception() {
		return _exception;
	}

	public void setException( Throwable value ) {
		exceptionParser = new WOExceptionParser( value );
		_exception = value;
	}

	/**
	 * @return bundle of the class currently being iterated over in the UI (if any)
	 */
	public NSBundle currentBundle() {
		return bundleForClassName( currentErrorLine.packageClassPath() );
	}

	/**
	 * @return Name of the jar file the class producing the error is located in
	 */
	public String currentJarName() {
		try {
			final String fullClassName = currentErrorLine.packageName() + "." + currentErrorLine.className();
			final Class<?> klass = Class.forName( fullClassName );
			final String pathtoJar = klass.getProtectionDomain().getCodeSource().getLocation().toString();
			return pathtoJar.substring(pathtoJar.lastIndexOf('/')+1);
		}
		catch( Exception e ) {
			return null;
		}
	}

	/**
	 * Provided for convenience when overriding Application.reportException(). Like so:
	 *
	 * @Override
	 * public WOResponse reportException( Throwable exception, WOContext context, NSDictionary extraInfo ) {
	 *    return ERXExceptionPage.reportException( exception, context, extraInfo );
	 * }
	 * 
	 * @deprecated since this page is now automatically used (Due to it being called WOExceptionPage)
	 */
	@Deprecated
	public static WOResponse reportException( Throwable exception, WOContext context, Map extraInfo ) {
		WOExceptionPage nextPage = ERXApplication.erxApplication().pageWithName( WOExceptionPage.class, context );
		nextPage.setException( exception );
		return nextPage.generateResponse();
	}

	/**
	 * @return The bundle containing the (fully qualified) named class. Null if class is not found or not contained in a bundle.
	 */
	private static NSBundle bundleForClassName( String fullyQualifiedClassName ) {
		Class<?> clazz;

		try {
			clazz = Class.forName( fullyQualifiedClassName );
		}
		catch( ClassNotFoundException e ) {
			return null;
		}

		return NSBundle.bundleForClass( clazz );
	}

	/**
	 * @return The CSS class(es) for the current row in the stack trace table.
	 *         Drives the visual treatment: own-app frames stand out, workspace
	 *         frames are marked, and external (framework/library) frames are
	 *         dimmed so the actionable frames are the ones that catch the eye.
	 *         Rows that can be opened in the IDE also get a {@code clickable}
	 *         class (cursor + hover affordance), matching the presence of the
	 *         row's {@code onclick} handler.
	 */
	public String currentRowClass() {
		final String categoryClass;

		switch( currentFrameCategory() ) {
			case MAIN:
				categoryClass = "frame-main";
				break;
			case WORKSPACE:
				categoryClass = "frame-workspace";
				break;
			default:
				categoryClass = "frame-external";
				break;
		}

		if( currentOpenInIDEURL() != null ) {
			return categoryClass + " clickable";
		}

		return categoryClass;
	}

	/**
	 * @return The bare dev-server URL for opening the current frame's source in
	 *         the IDE, or {@code null} if this frame can't be navigated to.
	 *
	 * A URL is produced when:
	 * <ul>
	 *   <li>the app is in development mode (a localhost dev server could
	 *       plausibly be listening), and</li>
	 *   <li>the frame has a real line number.</li>
	 * </ul>
	 *
	 * The dev server is loopback-only and password-free, so no password is
	 * required. For backward compatibility with users still running classic
	 * WOLips (whose server checks a password), a {@code pw} parameter is
	 * included <em>only if</em> {@code wolips.password} is defined — the
	 * password-free Parsley server simply ignores it.
	 *
	 * <p>This returns the raw URL (not wrapped in a {@code javascript:} call),
	 * suitable for handing to the page's {@code invokeURL()} helper from a row
	 * click handler. {@link #currentLineURL()} returns the wrapped form for use
	 * as an anchor {@code href}.
	 */
	public String currentOpenInIDEURL() {
		// Only offer links in dev mode — in production there's no IDE server to talk to.
		if( !ERXApplication.isDevelopmentModeSafe() ) {
			return null;
		}

		// WOExceptionParser returns "NA" for lines without a number (native/compiled code etc.)
		if( "NA".equals( currentErrorLine.lineNumber() ) ) {
			return null;
		}

		final int wolipsPortnumber = ERXProperties.intForKeyWithDefault( "wolips.port", 9485 );
		final String applicationName = application().name();
		final String className = currentErrorLine.packageName() + "." + currentErrorLine.className();
		final int lineNumber = currentErrorLine.line();

		final StringBuilder url = new StringBuilder( "http://localhost:" )
				.append( wolipsPortnumber )
				.append( "/openJavaFile?app=" ).append( applicationName )
				.append( "&className=" ).append( className )
				.append( "&lineNumber=" ).append( lineNumber );

		// Include the password only if one is configured — for classic WOLips
		// compatibility. The Parsley dev server ignores it.
		final String wolipsPassword = ERXProperties.stringForKey( "wolips.password" );
		if( wolipsPassword != null ) {
			url.append( "&pw=" ).append( wolipsPassword );
		}

		return url.toString();
	}

	/**
	 * @return The "open in IDE" URL wrapped in a {@code javascript:invokeURL(...)}
	 *         call, for use as an anchor {@code href}; or {@code null} if the
	 *         frame can't be navigated to.
	 */
	public String currentLineURL() {
		final String url = currentOpenInIDEURL();

		if( url == null ) {
			return null;
		}

		return "javascript:invokeURL('%s')".formatted( url );
	}

	/**
	 * @return True if the current line can't be navigated to.
	 */
	public boolean currentLineDisabled() {
		return currentOpenInIDEURL() == null;
	}

	/**
	 * @return A JavaScript snippet for the current row's {@code onclick}, or
	 *         {@code null} for frames that can't be navigated to (so those rows
	 *         get no click handler). Wired onto the whole {@code <tr>} so the
	 *         entire row is a click target, not just the line number.
	 */
	public String currentRowOnClick() {
		final String url = currentOpenInIDEURL();

		if( url == null ) {
			return null;
		}

		return "invokeURL('%s')".formatted( url );
	}

	public static class WOExceptionParser {

		private List<WOParsedErrorLine> _stackTrace;
		private Throwable _exception;
		private String _message;
		private String _typeException;

		public WOExceptionParser(Throwable exception) {
			_stackTrace = new ArrayList<>();
			_exception = NSForwardException._originalThrowable(exception);
			_message = _exception.getMessage();
			_typeException = _exception.getClass().getName();
			_parseException();
		}

		private List _ignoredPackages() {
			NSBundle bundle;
			String path, content;
			Map dic = null;
			List<NSBundle> allBundles = new ArrayList<>(NSBundle.frameworkBundles());
			List<String> ignored = new ArrayList<>();

			for (Enumeration enumerator = Collections.enumeration(allBundles); enumerator.hasMoreElements();) {
				bundle = (NSBundle) enumerator.nextElement();
				path = WOApplication.application().resourceManager().pathForResourceNamed("WOIgnoredPackage.plist", bundle.name(), null);
				if (path != null) {
					content = _stringFromFileSafely(path);
					if (content != null) {
						dic = (Map) NSPropertyListSerialization.propertyListFromString(content);
						if (dic != null && dic.containsKey("ignoredPackages")) {
							@SuppressWarnings("unchecked")
							List<String> tmpArray = (List<String>) dic.get("ignoredPackages");
							if (tmpArray != null && tmpArray.size() > 0) {
								ignored.addAll(tmpArray);
							}
						}
					}
				}
			}

			return ignored;
		}

		private void _verifyPackageForLine(WOParsedErrorLine line, List packages) {
			Enumeration enumerator;
			String ignoredPackageName, linePackageName;
			linePackageName = line.packageName();
			enumerator = Collections.enumeration(packages);

			while (enumerator.hasMoreElements()) {
				ignoredPackageName = (String) enumerator.nextElement();
				if (linePackageName.startsWith(ignoredPackageName)) {
					line.setIgnorePackage(true);
					break;
				}
			}
		}

		private void _parseException() {
			StringWriter sWriter = new StringWriter();
			PrintWriter pWriter = new PrintWriter(sWriter, false);
			String string;
			List lines;
			List ignoredPackage;
			WOParsedErrorLine aLine;
			String line;

			int i, size;
			try {
				_exception.printStackTrace(pWriter);
				pWriter.close();
				sWriter.close(); // Added the try/catch as this throws in JDK 1.2
									// aB.
				string = sWriter.toString();
				i = _exception.toString().length(); // We skip the name of the
													// exception and the message for
													// our parse
				if (string.length() > i + 2) { // certain errors don't contain a
												// stack trace
					string = string.substring(i + 2); // Skip the exception type and
														// message
					lines = Arrays.asList(string.split("\n"));
					ignoredPackage = _ignoredPackages();
					size = lines.size();
					_stackTrace = new ArrayList(size);
					for (i = 0; i < size; i++) {
						line = ((String) lines.get(i)).trim();
						if (line.startsWith("at ")) {
							// If we don't have an open parenthesis it means that we
							// have probably reach the latest stack trace.
							aLine = new WOParsedErrorLine(line);
							_verifyPackageForLine(aLine, ignoredPackage);
							_stackTrace.add(aLine);
						}
					}
				}
			}
			catch (Throwable e) {
				logger.error("WOExceptionParser - exception collecting backtrace data " + e + " - Empty backtrace.");
				logger.error( "", e);
			}
			if (_stackTrace == null) {
				_stackTrace = new ArrayList();
			}
		}

		public List<WOParsedErrorLine> stackTrace() {
			return _stackTrace;
		}

		public String typeException() {
			return _typeException;
		}

		public String message() {
			return _message;
		}

		/**
		 * Return a string from the contents of a file, returning null instead of any possible exception.
		 */
		private static String _stringFromFileSafely(String path) {
			File f = new File(path);

			if (!f.exists()) {
				return null;
			}

			FileInputStream fis = null;
			byte[] data = null;

			int bytesRead = 0;

			try {
				int size = (int) f.length();
				fis = new FileInputStream(f);
				data = new byte[size];

				while (bytesRead < size) {
					bytesRead += fis.read(data, bytesRead, size - bytesRead);
				}

			}
			catch (java.io.IOException e) {
				return null;
			}
			finally {
				if (fis != null) {
					try {
						fis.close();
					}
					catch (java.io.IOException e) {
						logger.debug("Exception while closing file input stream: " + e.getMessage());
						logger.debug("", e);
					}
				}
			}

			if (bytesRead == 0)
				return null;
			return new String(data);
		}

		/**
		 * WOParsedErrorLine is the class that will parse an exception line. After
		 * parsing a line (see format in the constructor comment), each instance
		 * will be able to get information about the line, class, method where the
		 * error occurs.
		 *
		 * Evolution : should rewrite the parsing stuff... And verify the real
		 * format of java exception... Be careful, apparently it could happen that
		 * the latest ")" on a line is not present. This is why in the parsing stuff
		 * I try to get the index of this closing parenthesis.
		 */
		public static class WOParsedErrorLine {
			private String _packageName;
			private String _className;
			private String _methodName;
			private String _fileName;
			private int _line;
			private boolean _ignorePackage; // if true, then it will not be
												// possible to display an hyperlink

			public WOParsedErrorLine(String line) {
				// line should have the format of an exception, which is normally
				// (below the index value)
				// at my.package.name.MyClass.myMethod(FileName.java:lineNumber)
				// ^ ^ ^ ^
				// atIndex I classIndex lineIndex
				// methodIndex
				int atIndex, methodIndex, classIndex, lineIndex, index;
				String string;
				atIndex = line.indexOf("at ") + 3;
				classIndex = line.indexOf('(') + 1;
				methodIndex = line.lastIndexOf('.', classIndex - 2) + 1;
				lineIndex = line.lastIndexOf(':');
				if (lineIndex < 0) { // We could potentially do not have the info if
										// we use a JIT
					_line = -1;
					_fileName = null;
				}
				else {
					lineIndex++;
					// Parse the line number
					index = line.indexOf(')', lineIndex);
					if (index < 0) {
						index = line.length();
					}

					string = line.substring(lineIndex, index); // Remove the last
																// ")"

					try {
						_line = Integer.parseInt(string); // Parse the fileName
						_fileName = line.substring(classIndex, lineIndex - 1);
					}
					catch (NumberFormatException ex) {
						_line = -1;
						_fileName = null;
					}
				}
				_methodName = line.substring(methodIndex, classIndex - 1);
				_packageName = line.substring(atIndex, methodIndex - 1);
				index = _packageName.lastIndexOf('.');
				if (index >= 0) {
					_className = _packageName.substring(index + 1);
					_packageName = _packageName.substring(0, index);
				}
				else
					_className = _packageName;
				if (_line < 0) {
					// JIT Activated so we don't have the class name... we can guess
					// it by using the package info\
					_fileName = _className + ".java";
				}
				_ignorePackage = false; // By default we handle all packages
			}

			public String packageName() {
				return _packageName;
			}

			public String className() {
				return _className;
			}

			public String packageClassPath() {
				if (_packageName.equals(_className)) {
					return _className;
				}
				return _packageName + "." + _className;
			}

			public String methodName() {
				return _methodName;
			}

			public boolean isDisable() {
				return _line < 0 || _ignorePackage;
			}

			private void setIgnorePackage(boolean yn) {
				_ignorePackage = yn;
			}

			public String fileName() {
				return _fileName;
			}

			public String lineNumber() {
				if (_line >= 0) {
					return String.valueOf(_line);
				}

				return "NA";
			}

			public int line() {
				return _line;
			}

			@Override
			public String toString() {
				final String lineInfo = (_line >= 0) ? String.valueOf(_line) : "No line info due to compiled code";
				final String fileInfo = (_line >= 0) ? _fileName : "Compiled code no file info";

				if (_packageName.equals(_className)) {
					return "class : " + _className + ": " + _methodName + " in file :" + fileInfo + " - line :" + lineInfo;
				}

				return "In package : " + _packageName + ", class : " + _className + " method : " + _methodName + " in file :" + fileInfo + " - line :" + lineInfo;
			}
		}
	}
}