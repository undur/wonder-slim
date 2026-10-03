package bookclubs.components;

import com.webobjects.appserver.WOContext;

import bookclubs.data.Library.Member;

public class MemberPage extends BaseComponent {

	public Member member;

	public MemberPage( final WOContext context ) {
		super( context );
	}
}
