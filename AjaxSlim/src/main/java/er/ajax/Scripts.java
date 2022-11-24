package er.ajax;

public class Scripts {

	private static final String WONDER_SLIM_JS = "wonder-slim.js";
	private static final String EMPTY_JS = "wonder-slim.js";

	private static final String WONDER_JS = "wonder.js";
	private static final String PROTOTYPE_JS = "prototype.js";
	private static final String SCRIPTACULOUS_JS = "scriptaculous.js";
	private static final String EFFECTS_JS = "effects.js";

	public static String wonderJS() {
		return useNew ? WONDER_SLIM_JS : WONDER_JS;
	}

	public static String prototypeJS() {
		return useNew ? EMPTY_JS : PROTOTYPE_JS;
	}

	public static String scriptaculousJS() {
		return useNew ? EMPTY_JS : SCRIPTACULOUS_JS;
	}

	public static String effectsJS() {
		return useNew ? EMPTY_JS : EFFECTS_JS;
	}

	/**
	 * Indicates whether we want to use the new script files
	 */
	public static boolean useNew = true;
}