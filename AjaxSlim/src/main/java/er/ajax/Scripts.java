package er.ajax;

public class Scripts {

	/**
	 * Indicates whether we want to use the new script files.
	 * 
	 * Set to true for madness.
	 */
	public static boolean useNewScripts = false;

	/**
	 * Experimental JS file (what will probably eventually replace wonder.js)
	 */
	private static final String WONDER_SLIMMER_JS = "wonder-slimmer.js";
	
	/**
	 * Scratchpad JS-file combining old/new work
	 */
	private static final String WONDER_SLIM_JS = "wonder-slim.js";
	
	/**
	 * Placeholder for files that will eventually be omitted
	 */
	private static final String EMPTY_JS = "empty.js";

	// The old files
	private static final String WONDER_JS = "wonder.js";
	private static final String PROTOTYPE_JS = "prototype.js";
	private static final String SCRIPTACULOUS_JS = "scriptaculous.js";
	private static final String EFFECTS_JS = "effects.js";

	public static String wonderJS() {
		return useNewScripts ? WONDER_SLIMMER_JS : WONDER_JS;
	}

	public static String prototypeJS() {
		return useNewScripts ? EMPTY_JS : PROTOTYPE_JS;
	}

	public static String scriptaculousJS() {
		return useNewScripts ? EMPTY_JS : SCRIPTACULOUS_JS;
	}

	public static String effectsJS() {
		return useNewScripts ? EMPTY_JS : EFFECTS_JS;
	}
}