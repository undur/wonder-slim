package er.extensions.localization;

/**
 * Overrides <code>plurifiedString</code> from its super class and cancels all
 * plural form translations including the one provided by
 * <code>plurifiedStringWithTemplateForKey</code>.
 * Good for languages that don't have plural forms (such as Japanese).
 */
public class ERXNonPluralFormLocalizer extends ERXLocalizer {

	public ERXNonPluralFormLocalizer(String aLanguage) {
		super(aLanguage);
	}

	@Override
	public String plurifiedString(String name, int count) {
		return name;
	}

	@Override
	public String toString() {
		return "<ERXNonPluralFormLocalizer " + language + ">";
	}
}