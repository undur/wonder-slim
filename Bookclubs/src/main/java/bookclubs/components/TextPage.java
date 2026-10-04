package bookclubs.components;

import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOContext;
import com.webobjects.appserver.WOResponse;

import bookclubs.data.Library.Club;

/**
 * A page of text, with a status of its choosing
 */
public class TextPage extends BaseComponent {

	public String title;
	public String text;
	private int _status = 200;

	public TextPage( final WOContext context ) {
		super( context );
	}

	public static TextPage create( final WOContext context, final Club club, final String title, final String text ) {
		final TextPage page = (TextPage)WOApplication.application().pageWithName( TextPage.class.getName(), context );
		page.club = club;
		page.title = title;
		page.text = text;
		return page;
	}

	public TextPage status( final int status ) {
		_status = status;
		return this;
	}

	@Override
	public WOResponse generateResponse() {
		final WOResponse response = super.generateResponse();
		response.setStatus( _status );
		return response;
	}
}
