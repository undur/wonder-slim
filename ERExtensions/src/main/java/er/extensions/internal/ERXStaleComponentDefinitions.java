package er.extensions.internal;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects._ideservices._NSProjectBundleIDEProject;
import com.webobjects._ideservices._WOProject;
import com.webobjects.appserver.WOApplication;
import com.webobjects.appserver.WOResourceManager;
import com.webobjects.appserver._private.WOComponentDefinition;
import com.webobjects.appserver._private.WOProjectBundle;
import com.webobjects.foundation.NSArray;
import com.webobjects.foundation.NSBundle;

/**
 * Recovers a component created while the application runs in development, which would otherwise render as nothing
 * until a restart (#171).
 *
 * In development, resources are found through an index of each project's files, rescanned after a miss at most once a
 * minute. A component used for the first time within that minute has its class but not its template, and WebObjects
 * caches a definition without a template for good. When a definition has no template but its {@code .wo} is in a
 * project on disk, the index is stale: we rescan the projects, clear the definition cache and look the component up
 * again.
 */

public class ERXStaleComponentDefinitions {

	private static final Logger logger = LoggerFactory.getLogger( ERXStaleComponentDefinitions.class );

	/**
	 * Components whose template the rescan didn't bring in, so we don't rescan for them on every lookup
	 */
	private static final Set<String> _unrecoverable = ConcurrentHashMap.newKeySet();

	/**
	 * @return true if the definition lacks a template that exists in a project on disk, so a lookup made after a rescan
	 *         would find it
	 */
	public static boolean isStale( final String componentName, final WOComponentDefinition definition ) {

		if( definition == null || definition.pathURL() != null || _unrecoverable.contains( componentName ) ) {
			return false;
		}

		return templateExists( componentName );
	}

	/**
	 * Rescans the project bundles and clears the component definition cache, for a lookup to find the new template
	 */
	public static void refresh( final WOApplication application ) {
		final WOResourceManager resourceManager = application.resourceManager();

		rescan( resourceManager._appProjectBundle() );

		for( final Object bundle : resourceManager._frameworkProjectBundles() ) {
			rescan( bundle );
		}

		application._removeComponentDefinitionCacheContents();
	}

	/**
	 * Records a component still missing its template after a refresh, and says why
	 */
	public static void unrecoverable( final String componentName ) {
		if( _unrecoverable.add( componentName ) ) {
			logger.warn( "The template of component '{}' exists on disk, but the application doesn't find it. It renders as nothing until a restart", componentName );
		}
	}

	/**
	 * @return true if a bundle has the component's template
	 */
	private static boolean templateExists( final String componentName ) {
		final String templateName = componentName.substring( componentName.lastIndexOf( '.' ) + 1 ) + ".wo";

		if( NSBundle.mainBundle().pathURLForResourcePath( templateName ) != null ) {
			return true;
		}

		for( final NSBundle bundle : (NSArray<NSBundle>)NSBundle.frameworkBundles() ) {
			if( bundle.pathURLForResourcePath( templateName ) != null ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Has the project rebuild its list of files and add them to the index. The list is private and kept until the
	 * rescan interval passes, so we drop it through reflection.
	 */
	private static void rescan( final Object bundle ) {

		if( !(bundle instanceof WOProjectBundle projectBundle) ) {
			return;
		}

		final _WOProject woProject = projectBundle._woProject();

		if( woProject == null || !(woProject.ideProject() instanceof _NSProjectBundleIDEProject ideProject) ) {
			return;
		}

		try {
			final Field resources = _NSProjectBundleIDEProject.class.getDeclaredField( "_resources" );
			resources.setAccessible( true );

			synchronized( ideProject ) {
				resources.set( ideProject, null );
				ideProject.extractFilesIntoWOProject( woProject );
			}
		}
		catch( ReflectiveOperationException e ) {
			logger.warn( "Couldn't rescan the project at {}", ideProject.bundlePath(), e );
		}
	}
}
