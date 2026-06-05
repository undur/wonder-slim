package ajaxplayground.components;

import com.webobjects.appserver.WOActionResults;
import com.webobjects.appserver.WOContext;

import er.extensions.components.ERXComponent;

import ajaxplayground.components.gallery.GalleryUpdateContainer;
import ajaxplayground.components.scenario.ScenarioAccumulation;
import ajaxplayground.components.scenario.ScenarioFocus;
import ajaxplayground.components.scenario.ScenarioNested;
import ajaxplayground.components.scenario.ScenarioMultiObserve;
import ajaxplayground.components.scenario.ScenarioScripts;
import ajaxplayground.components.scenario.ScenarioUuidIds;

/**
 * Navigation index for the playground. Links to every component-gallery page and every
 * danger-matrix scenario page. Each action just returns a fresh page instance.
 */
public class Main extends ERXComponent {

	public Main( WOContext context ) {
		super( context );
	}

	public WOActionResults scenarioFocus() {
		return pageWithName( ScenarioFocus.class );
	}

	public WOActionResults scenarioAccumulation() {
		return pageWithName( ScenarioAccumulation.class );
	}

	public WOActionResults scenarioNested() {
		return pageWithName( ScenarioNested.class );
	}

	public WOActionResults scenarioUuidIds() {
		return pageWithName( ScenarioUuidIds.class );
	}

	public WOActionResults scenarioScripts() {
		return pageWithName( ScenarioScripts.class );
	}

	public WOActionResults scenarioMultiObserve() {
		return pageWithName( ScenarioMultiObserve.class );
	}

	public WOActionResults galleryUpdateContainer() {
		return pageWithName( GalleryUpdateContainer.class );
	}
}
