package er.extensions.appserver;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;

public class ERXResponse extends WOResponse {

	private WOContext _context;

	public ERXResponse() {}

	public ERXResponse(String content, int status) {
		this(content);
		setStatus(status);
	}

	public ERXResponse(String content) {
		setContent(content);
	}

	public ERXResponse(WOContext context) {
		_context = context;
	}
}