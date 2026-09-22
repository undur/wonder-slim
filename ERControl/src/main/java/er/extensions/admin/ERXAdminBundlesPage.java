package er.extensions.admin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.webobjects.appserver.WOContext;
import com.webobjects.foundation.NSBundle;

import er.extensions.components.ERXComponent;

/**
 * The application's main bundle and the frameworks loaded alongside it.
 */
public class ERXAdminBundlesPage extends ERXComponent {

	public NSBundle current;

	public ERXAdminBundlesPage( WOContext context ) {
		super( context );
	}

	public List<NSBundle> bundles() {
		final List<NSBundle> frameworks = new ArrayList<>( NSBundle.frameworkBundles() );
		frameworks.sort( Comparator.comparing( NSBundle::name, String.CASE_INSENSITIVE_ORDER ) );

		final List<NSBundle> result = new ArrayList<>();
		result.add( NSBundle.mainBundle() );
		result.addAll( frameworks );
		return result;
	}

	public int bundleCount() {
		return bundles().size();
	}

	public boolean currentIsMainBundle() {
		return current == NSBundle.mainBundle();
	}

	public String currentKind() {
		return current.getClass().getSimpleName();
	}

	public String currentPath() {
		return current.bundlePathURL() != null ? String.valueOf( current.bundlePathURL() ) : current.bundlePath();
	}

	public int currentClassCount() {
		return current.bundleClassNames() == null ? 0 : current.bundleClassNames().count();
	}
}
