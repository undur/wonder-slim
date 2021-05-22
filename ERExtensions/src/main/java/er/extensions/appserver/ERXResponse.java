package er.extensions.appserver;

import java.util.Objects;

import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;
import com.webobjects.foundation.NSMutableDictionary;

/**
 * ERXResponse provides a place to override methods of WOResponse. This is
 * returned by default from ERXApplication. Also has support for "partials",
 * i.e. in your render tree, you can define a new "partial", where the content
 * will actually get rendered.
 * 
 * @author mschrag
 * @author ak
 */
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

	/**
	 * WO 5.4 API Sets the value for key in the user info dictionary.
	 * 
	 * @param value
	 *            value to add to userInfo()
	 * @param key
	 *            key to add value under
	 */
	@Override
	public void setUserInfoForKey(Object value, String key) {
		Objects.requireNonNull(value);
		Objects.requireNonNull(key);
		
		NSMutableDictionary newUserInfo = new NSMutableDictionary(value, key);

		if (userInfo() != null) {
			newUserInfo.addEntriesFromDictionary(userInfo());
		}

		setUserInfo(newUserInfo);
	}

	/**
	 * WO 5.4 API
	 * 
	 * @param key
	 *            key to return value from userInfo() for
	 * @return value from {@link #userInfo()} for key, or <code>null</code> if not available
	 */
	@Override
	public Object userInfoForKey(String key) {
		Objects.requireNonNull(key);
		return userInfo() != null ? userInfo().objectForKey(key) : null;
	}
}