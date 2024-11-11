package er.extensions.statistics.store;

public class ERXNormalRequestDescription implements IERXRequestDescription {

	private final String _componentName;
	private final String _requestHandler;
	private final String _additionalInfo;

	public ERXNormalRequestDescription(String componentName, String requestHandler, String additionalInfo) {
		_componentName = componentName;
		_requestHandler = requestHandler;
		_additionalInfo = additionalInfo;
	}

	public String getComponentName() {
		return _componentName;
	}

	public String getRequestHandler() {
		return _requestHandler;
	}

	public String getAdditionalInfo() {
		return _additionalInfo;
	}

	public RequestDescriptionType getType() {
		return RequestDescriptionType.NORMAL;
	}

	@Override
	public String toString() {
		return _componentName + "-" + _requestHandler + _additionalInfo;
	}
}