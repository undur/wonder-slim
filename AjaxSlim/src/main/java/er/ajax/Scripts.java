package er.ajax;

public class Scripts {

	private static final String WONDER_SLIM_JS = "wonder-slimmer.js";
//	private static final String WONDER_SLIM_JS = "wonder-slim.js";
	private static final String EMPTY_JS = "empty.js";

	private static final String WONDER_JS = "wonder.js";
	private static final String PROTOTYPE_JS = "prototype.js";
	private static final String SCRIPTACULOUS_JS = "scriptaculous.js";
	private static final String EFFECTS_JS = "effects.js";

	public static String wonderJS() {
		return useNewScripts ? WONDER_SLIM_JS : WONDER_JS;
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

	/**
	 * Indicates whether we want to use the new script files
	 */
	public static boolean useNewScripts = false;
}