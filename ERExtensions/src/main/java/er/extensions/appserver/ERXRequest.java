package er.extensions.appserver;

import java.net.HttpCookie;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WORequest;
import com.webobjects.appserver._private.WOShared;
import com.webobjects.appserver._private.WOURLFormatException;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSData;
import com.webobjects.foundation.NSDictionary;
import com.webobjects.foundation.NSForwardException;
import com.webobjects.foundation.NSMutableDictionary;
import com.webobjects.foundation.NSTimestamp;

import er.extensions.ERXP;
import er.extensions.foundation.ERXProperties;

/**
 * The request is created via {@link ERXApplication#newRequest(String, String, String, Map, NSData, Map)} (the routing layer canonicalizes its URL first).
 */

public  class ERXRequest extends WORequest {

    private static final Logger log = LoggerFactory.getLogger(ERXRequest.class);

    private static final String UNKNOWN_HOST = "UNKNOWN";
    private static final String X_FORWARDED_PROTO_FOR_SSL = ERXProperties.stringForKeyWithDefault(ERXP.X_FORWARDED_PROTO_FOR_SSL.id(), "https");
    private static final String X_FORWARDED_PROTO_HEADER_KEY_FOR_SSL = ERXProperties.stringForKeyWithDefault(ERXP.X_FORWARDED_PROTO_HEADER_KEY_FOR_SSL.id(), "x-forwarded-proto");
    
    /**
     * Headers carrying the client's address as set by a WO adaptor, in the order they're checked
     */
    private static final String[] ADAPTOR_ADDRESS_HEADERS = {"x-webobjects-remote-addr", "remote_addr", "remote_host", "pc-remote-addr"};

    /**
     * 'Host' is the official HTTP 1.1 header for the host name in the request URL, so this should be checked first. @see http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.23
     * when the app is behind a reverse proxy 'Host' will contain the proxy address instead of the requested one so check first for 'x-forwarded-host' @see http://httpd.apache.org/docs/2.2/mod/mod_proxy.html#x-headers
     * Fallback headers such as server_name will screw up your complete URL generation for secure domains that have wildcard subdomains since it returns sth like *.domain.com for host name
     */
    private static final String[] HOST_NAME_HEADERS = {"x-forwarded-host", "Host", "x-webobjects-server-name", "server_name", "http_host"};
    
    /**
     * Specifies whether https should be overridden to be enabled or disabled app-wide. This is 
     * useful if you are developing with DirectConnect and you want to be able to specify secure 
     * forms and links, but you want to be able to continue testing them without setting up SSL.
     * 
     * Defaults to <code>false</code>, set er.extensions.ERXRequest.secureDisabled=true to turn it off.
     */
    private final boolean _secureDisabled;
    
    /**
     * Holds the cookies in a NSDictionary.
     */
    private NSDictionary<String, NSArray<String>> _cookieValues;
    
    /**
     * Initializes a ERXRequest object with the specified parameters
     * 
     * @param method a "GET", "POST" or "HEAD", may not be <code>null</code>. If <code>null</code>, or not one of the allowed methods, an IllegalArgumentException will be thrown
     * @param url a URL, may not be null or an IllegalArgumentException will be thrown
     * @param httpVersion  the version of HTTP used when sending the message, may not be <code>null</code> or an IllegalArgumentException will be thrown
     * @param headers  a dictionary whose String keys correspond to header names and whose values are arrays of one or more strings corresponding to the values of each header
     * @param content the HTML content
     * @param userInfo java.util.Map that contains any information that the WORequest object wants to pass along to other objects
     */
    public ERXRequest(String method, String url, String httpVersion, Map headers, NSData content, Map userInfo) {
        super(method, url, httpVersion, headers, content, userInfo);

        _secureDisabled = ERXRequest._isSecureDisabled();
    }
    
    /**
     * WO's context() creates a context for the request (WOApplication.createContextForRequest) when it has none. We
     * return only the context already attached, or null: a caller probing for a context, such as
     * ERXAppBasedResourceManager (which then falls back to ERXWOContext.currentContext()), must not create a stray
     * context as a side effect, which would also become the thread's current context.
     *
     * Added in Project Wonder when WO had no public accessor for the attached context:
     * https://github.com/wocommunity/wonder/commit/51106ce8b93372d88d0df2520003225b93f39f5a
     */
    @Override
    public WOContext context() {
    	return _context();
    }

    /**
     * Every WOContext sets itself as its request's context when it's constructed. A second context for a request that
     * already has one is a second request cycle inside the first (a direct action constructed from the request, another
     * handler's handleRequest(), createContextForRequest()): the request handler finishes only the context it created,
     * so whatever happens in the other one, such as a session created or logged in, is lost (#198).
     *
     * TODO: This should probably fail rather than log. It logs for now, so applications that do this can be found and
     * fixed before it does // Hugi 2026-10-10
     */
    @Override
    protected void _setContext(final WOContext context) {
    	final WOContext existing = _context();

    	if (context != null && existing != null && existing != context) {
    		log.error("A second context was created for {}, which already has one. The request handler finishes only its own, so a session created or changed in the other is lost (#198)", uri(), new IllegalStateException("Second context for request"));
    	}

    	super._setContext(context);
    }

    /**
     * This method is used by WOContext when generating full URLs for form actions in secure mode, etc.
     *
     * Overriding this because WORequest checks 'server_name' before 'Host' by default and it does not cut it for generating full secure
     * urls in the case of a hostname that uses a wildcard SSL certificate allowing infinite secure subdomains.
     *
     * For example, if we have a wildcard ssl cert for https://*.mydomain.com (where * = wildcard subdomain), and we use
     * subdomains to implement CSS skinning for different customers that are all using the
     * same WO app while using subdomains to get their "custom" site, with host names such as
     * acmesandwiches.mydomain.com, apple.mydomain.com, kfc.mydomain.com, etc., and we configure apache with one virtual host config for
     * *.mydomain.com, then the stupid 'server_name' header will return *.mydomain.com INSTEAD OF the host name
     * used in the request URL, and thus all https urls in forms, links etc will be broken.
     *
     * @return the server name, which happens to be used by WOContext for generating full URLs.
     * @see com.webobjects.appserver.WORequest#_serverName()
     * @see WOContext#completeURLWithRequestHandlerKey(String, String, String, String, boolean, int)
     * @see WORequest#_completeURLPrefix(StringBuffer, boolean, int)
     */
	@Override
	public String _serverName() {
		String serverName = headerForKey("x-webobjects-servlet-server-name");

		if(serverName == null || serverName.length() == 0) {
			if (isUsingWebServer()) {
				serverName = requestedHostFromHeaders(); // Checks host name keys in our preferred order instead of Apple WO 5.4.3's default header check logic

				if ((serverName == null) || (serverName.length() == 0) || serverName.equals(UNKNOWN_HOST)) {
					throw new NSForwardException(new WOURLFormatException("<" + super.getClass().getName() + ">: Unable to build complete url as no server name was provided in the headers of the request."));
				}
			}
			else {
				serverName = ERXApplication.erxApplication().publicHost();
			}
		}

		return serverName;
	}

    /**
     * @return The remote client host address, see {@link #remoteAddress(WORequest)}. Returns "UNKNOWN" if no address is found.
     */
    public String remoteHostAddress() {
    	final String address = remoteAddress(this);
    	return address != null ? address : UNKNOWN_HOST;
    }

	/**
	 * The address of the client that sent the request, for logging and display. In order:
	 *
	 * <ol>
	 * <li>The address a WO adaptor passes on (the <code>x-webobjects-remote-addr</code>, <code>remote_addr</code>, <code>remote_host</code> or <code>pc-remote-addr</code> header)</li>
	 * <li>The first address in <code>x-forwarded-for</code>, the client as a reverse proxy saw it</li>
	 * <li>The address of the connection the request came in on (when the application is reached directly)</li>
	 * </ol>
	 *
	 * Headers come from whoever sends the request, so when the application can be reached without passing through the adaptor or proxy
	 * that sets them, the client can put any address there. Don't base access decisions on this.
	 *
	 * @return The client's address, or null if none is found
	 */
	public static String remoteAddress( final WORequest request ) {

		for( final String headerName : ADAPTOR_ADDRESS_HEADERS ) {
			final String headerValue = request.headerForKey( headerName );

			if( headerValue != null && !headerValue.isBlank() ) {
				return headerValue.strip();
			}
		}

		final String forwardedFor = request.headerForKey( "x-forwarded-for" );

		if( forwardedFor != null ) {
			final String firstAddress = forwardedFor.split( "," )[0].strip();

			if( !firstAddress.isEmpty() ) {
				return firstAddress;
			}
		}

		final InetAddress originatingAddress = request._originatingAddress();

		if( originatingAddress != null ) {
			return originatingAddress.getHostAddress();
		}

		return null;
	}

    /**
     * @return The host the request was addressed to, from the first of {@link #HOST_NAME_HEADERS} present; "UNKNOWN" when none is
     */
    private String requestedHostFromHeaders() {

    	for (final String headerName : HOST_NAME_HEADERS) {
			final String headerValue = headerForKey(headerName);

			if (headerValue != null) {
				return headerValue;
			}
		}

    	return UNKNOWN_HOST;
    }

    /**
     * @return true if er.extensions.ERXRequest.secureDisabled is true. Defaults to false.
     */
    public static boolean _isSecureDisabled() {
        return ERXProperties.booleanForKeyWithDefault(ERXP.SECURE_DISABLED.id(), false);
    }
    
    /**
     * @return <code>true</code> if er.extensions.ERXRequest.secureDisabled is true
     */
    public boolean isSecureDisabled() {
    	return _secureDisabled;
    }
    
	/**
	 * The locale this request asks for, see {@link #requestedLocale()}
	 */
	private Locale _requestedLocale;

	/**
	 * Whether {@link #_requestedLocale} has been computed (it may legitimately be null)
	 */
	private boolean _requestedLocaleResolved;

	/**
	 * @return The locale this request asks for in its {@code Accept-Language} header, null when it names none. A hint
	 *         from the client: the framework never formats in it on its own; an application that wants to honour it
	 *         says so, for example by setting it as the session's locale ({@link ERXSession#setLocale(Locale)}).
	 */
	public Locale requestedLocale() {
		if (!_requestedLocaleResolved) {
			_requestedLocale = ERXLocale.fromAcceptLanguage(headerForKey("accept-language"), null);
			_requestedLocaleResolved = true;
		}

		return _requestedLocale;
	}

    
    /**
     * WO's version casts the result of SimpleDateFormat.parseObject() to NSTimestamp. That result is always a plain
     * java.util.Date, so every successful parse throws ClassCastException. We parse to a Date and wrap it.
     */
    @Override
    public NSTimestamp dateFormValueForKey(String key, SimpleDateFormat dateFormatter) {

        final String dateString = stringFormValueForKey(key);
        Date date = null;

        if (dateString != null && dateFormatter != null) {
            try {
                date = dateFormatter.parse(dateString);
            }
            catch (java.text.ParseException e) {
               log.error("Could not parse date '{}'.", dateString, e);
            }
        }

        return date == null ? null : new NSTimestamp(date);
    }

    /**
     * @return Whether or not this request is secure
     */
    @Override
    public boolean isSecure() {
    	return ERXRequest.isRequestSecure(this);
    }
    
    /**
     * Add the protocol, server and port parts to a StringBuffer to build an URL to this app.
     * if port is set to 0, this request port will be used.
     *
     * Differs from WO's, which always appends ":port", including :80 and :443:
     * <ul>
     * <li>The default port of the scheme is omitted. A complete URL that spells it out has a different origin, as far as
     * browsers are concerned, from the page it's used on (http://host vs. http://host:80), which broke Ajax form
     * submits.</li>
     * <li>Without direct connect no port is appended at all: the request came through a web server, and the port the
     * application instance listens on isn't the public one.</li>
     * <li>er.extensions.ERXRequest.secureDisabled turns https URLs into http ones, for development without TLS.</li>
     * </ul>
     *
     * @param secure generate a https url
     * @param port the port number to use, 0 this request port
     */
	@Override
	public void _completeURLPrefix(StringBuffer stringbuffer, boolean secure, int port) {

    	if (_secureDisabled) {
    		secure = false;
    	}
    	
    	String serverName = _serverName();
        String portStr;

        if (port == 0) {
        	portStr = secure ? "443" : this._serverPort();
        }
        else {
        	portStr = WOShared.unsignedIntString(port);
        }

        if (secure) {
        	stringbuffer.append("https://");
        }
        else {
        	stringbuffer.append("http://");
        }

   		stringbuffer.append(serverName);

   		if(portStr != null && WOApplication.application().isDirectConnectEnabled() && ((secure && !"443".equals(portStr)) || (!secure && !"80".equals(portStr)))) {
   			stringbuffer.append(':');
   			stringbuffer.append(portStr);
        }
    }
    
    /**
     * MS: I found this somewhere else a while ago, but I have no idea where or I'd give attribution.
     * 
     * @param request the request to check
     * @return whether or not the given request is secure.
     */
    public static boolean isRequestSecure(WORequest request) {
        boolean isRequestSecure = false;
        
        // Depending on the adaptor the incoming port can be found in one of two places
        if (request != null) {
        	
	        String serverPort = request.headerForKey("SERVER_PORT");
	        if (serverPort == null) {
	        	serverPort = request.headerForKey("x-webobjects-servlet-server-port");
	        }
	        if (serverPort == null) {
	        	serverPort = request.headerForKey("x-webobjects-server-port");
	        }
	
	        // Apache and some other web servers use this to indicate HTTPS mode.
	        String httpsMode = request.headerForKey("https");
	
	        // If either the https header is 'on' or the server port is 443 then we
	        // consider this to be an HTTPS request (as WO's own isSecure() does).
	        if (httpsMode != null && httpsMode.equalsIgnoreCase("on")) {
	        	isRequestSecure = true;
	        }
	        else if ("443".equals(serverPort)) {
	        	isRequestSecure = true;
	        }
	        
	        // Check if we've got an x-forwarded-proto header which is typically sent by a load balancer that is 
	        // implementing ssl termination to indicate the request on the public side of the load balancer is secure.
	        else if (X_FORWARDED_PROTO_FOR_SSL.equals(request.headerForKey(X_FORWARDED_PROTO_HEADER_KEY_FOR_SSL))) {
	    		isRequestSecure = true;
	        }
        }

        return isRequestSecure;
    }

    /**
     * Overridden to use our own method for cookie parsing
     */
    @Override
	public NSDictionary cookieValues() {
        if (_cookieValues == null) {
        	_cookieValues = parseCookieValues( this );
        }

        return _cookieValues;
    }    

    /**
     * More graceful handling of a malformed cookie header than WO's.
     * Parses cookies one at a time. If a malformed cookie is present,
     * discards only the malformed cookies rather than all of them.
     * 
     * @return Parsed valid cookies from the given request 
     */
	private static NSDictionary<String, NSArray<String>> parseCookieValues( final WORequest request ) {
		final NSMutableDictionary<String, NSArray<String>> cookieDictionary = new NSMutableDictionary<>();

		String cookieHeader = request.headerForKey("cookie");

		if (cookieHeader == null || cookieHeader.length() == 0) {
			// The IIS adaptor passes cookies in a header of its own
			cookieHeader = request.headerForKey("http_cookie");
		}

		if (cookieHeader != null && cookieHeader.length() > 0) {
			final String[] cookies = cookieHeader.split(";");

			for (int i = 0; i < cookies.length; i++) {
				try {
					// only parse one cookie at a time => get(0)
					final HttpCookie httpCookie = HttpCookie.parse(cookies[i]).get(0);

					// A browser lists cookies with longer (more specific) paths before those with shorter paths
					// (https://stackoverflow.com/a/24214538). Every value is kept, in that order, so the first value
					// of a name is the most specific one.
					final String cookieName  = httpCookie.getName();
					final String cookieValue = httpCookie.getValue();

					NSArray<String> cookieValueArray = cookieDictionary.get(cookieName);

					if ( cookieValueArray == null ){
						cookieValueArray = new NSArray<>();
					}

					cookieValueArray = cookieValueArray.arrayByAddingObject(cookieValue);
					cookieDictionary.put( cookieName, cookieValueArray );
				}
				catch (RuntimeException e) {
					log.warn("Unable to parse cookie '{}': {}", cookies[i], e.getMessage());
				}
			}
		}

		return cookieDictionary.immutableClone();
	}

    /**
     * Overridden because the super implementation would pull in all 
     * content even if the request is supposed to be streaming and thus 
     * very large. Will now return <code>false</code> if the request
     * handler is streaming.
     *
     * WO only asks this while the application is refusing new sessions (the action request handlers' check), so a
     * streaming request that carries a session cookie is treated as new there.
     *
     * @return <code>true</code> if the session ID can be obtained from the form values or a cookie.
     */
	@Override
	public boolean isSessionIDInRequest() {
		ERXApplication app = (ERXApplication) WOApplication.application();

		if (app.isStreamingRequestHandlerKey(requestHandlerKey())) {
			return false;
		}

		return super.isSessionIDInRequest();
	}

    /**
     * Overridden because the super implementation would pull in all 
     * content even if the request is supposed to be streaming and thus 
     * very large. For a streaming request handler key (WO's own, or one registered with
     * ERXApplication.registerStreamingRequestHandlerKey) the session ID is looked up in the cookies only: reading a
     * form value would read the whole request body into memory. WO applies this to its own streaming key only.
     *
     * @param inCookiesFirst define if session ID should be searched first in cookie
     */
    @Override
	protected String _getSessionIDFromValuesOrCookie(boolean inCookiesFirst) {
        ERXApplication app = (ERXApplication)WOApplication.application();
        String sessionIdKey = WOApplication.application().sessionIdKey();

        boolean wis = WOApplication.application().streamActionRequestHandlerKey().equals(requestHandlerKey());
        boolean alternateStreaming = app.isStreamingRequestHandlerKey(requestHandlerKey());
        boolean streaming = wis || alternateStreaming;
        
        String sessionID = null;
        if(inCookiesFirst) {
            sessionID = cookieValueForKey(sessionIdKey);
            if(sessionID == null && !streaming) {
                sessionID = stringFormValueForKey(sessionIdKey);
            }
        } else {
            if(!streaming) {
                sessionID = stringFormValueForKey(sessionIdKey);
            }
            if(sessionID == null) {
                sessionID = cookieValueForKey(sessionIdKey);
            }
        }
        return sessionID;
    }
}