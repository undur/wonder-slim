package er.extensions.projectlayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.webobjects.foundation.development.NSResourceType;
import com.webobjects.foundation.development.NSStandardProjectBundle;

/**
 * A project bundle that finds its resources in the folders its {@link ERXProjectLayout} declares.
 *
 * NSProjectBundle guesses a resource's type from its extension, and a guess can be wrong (a .css kept with the
 * woresources, say). So each type looks in its own folder first, then in the others.
 */
public class ERXProjectLayoutBundle extends NSStandardProjectBundle {

	private final ERXProjectLayout _layout;

	public ERXProjectLayoutBundle( final String projectPath, final Properties buildProperties, final ERXProjectLayout layout ) {
		super( projectPath, buildProperties, null );
		_layout = layout;
	}

	public ERXProjectLayout layout() {
		return _layout;
	}

	@Override
	public List<String> relativePathForResourceType( final NSResourceType type ) {
		final List<String> paths = new ArrayList<>();

		switch( type ) {
			case Component:
				paths.add( _layout.components() );
				break;
			case Model:
			case D2WModel:
			case Strings:
			case InfoPlist:
				paths.add( _layout.woresources() );
				break;
			case WebServer:
			case JavaClientResources:
				paths.add( _layout.webserverResources() );
				break;
			default:
				break;
		}

		addIfAbsent( paths, _layout.woresources() );
		addIfAbsent( paths, _layout.components() );
		addIfAbsent( paths, _layout.webserverResources() );

		return paths;
	}

	private static void addIfAbsent( final List<String> paths, final String path ) {
		if( !paths.contains( path ) ) {
			paths.add( path );
		}
	}
}
