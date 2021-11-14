package er.extensions.appserver;

import com.webobjects.appserver.WOResponse;

public class ERXResponse extends WOResponse {

	public ERXResponse() {}

	public ERXResponse(String content, int status) {
		this(content);
		setStatus(status);
	}

	public ERXResponse(String content) {
		setContent(content);
	}
}