package er.extensions.statistics.store;

public class ERXEmptyRequestDescription implements IERXRequestDescription {

	private static final String DEFAULT_ERROR_STRING = "Error-during-context-description";

	private String _descriptionString = DEFAULT_ERROR_STRING;

	public ERXEmptyRequestDescription(String descriptionString) {
		if (descriptionString != null) {
			_descriptionString = descriptionString;
		}
	}

	public String getComponentName() {
		throw new IllegalStateException("field was not set use toString method instead!");
	}

	public String getRequestHandler() {
		throw new IllegalStateException("field was not set use toString method instead!");
	}

	public String getAdditionalInfo() {
		throw new IllegalStateException("field was not set use toString method instead!");
	}

	public RequestDescriptionType getType() {
		return RequestDescriptionType.EMPTY;
	}

	@Override
	public String toString() {
		return _descriptionString;
	}
}