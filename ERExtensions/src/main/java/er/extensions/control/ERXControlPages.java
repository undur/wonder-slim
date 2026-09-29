package er.extensions.control;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import com.webobjects.appserver.WOComponent;

/**
 * The pages of the control panel, as registered by the framework, plugins, the application or anything else. ERControl
 * shows them when it's present: each in its layout, behind its login, at {@code /wonder/admin/<name>}, listed in its
 * navigation by category. Without ERControl, what's registered goes unused, so registering never requires it.
 *
 * <pre>{@code
 * ERXControlPages.register( new ERXControlPages.Page( "Billing", "invoices", "Invoices", "Invoices waiting to be sent.", InvoicesControlPage.class ) );
 * }</pre>
 *
 * The categories are listed with {@link #FRAMEWORK_CATEGORY} first, then in the order they were first registered, each
 * with its pages in the order they were registered.
 */
public final class ERXControlPages {

	/**
	 * The category of the framework's own pages, listed first
	 */
	public static final String FRAMEWORK_CATEGORY = "wonder-slim";

	/**
	 * A page of the control panel.
	 *
	 * @param category The category it's listed under in the navigation
	 * @param name Its path beneath the control panel's, unique among the pages; empty for the control panel's front page
	 * @param title Its title, in the navigation and at the top of the page
	 * @param description A line beneath the title, null for none
	 * @param component The component rendering its content: ERControl supplies the layout around it
	 */
	public record Page( String category, String name, String title, String description, Class<? extends WOComponent> component ) {

		public Page {
			Objects.requireNonNull( category );
			Objects.requireNonNull( name );
			Objects.requireNonNull( title );
			Objects.requireNonNull( component );
		}
	}

	/**
	 * A category of pages, as listed in the navigation
	 */
	public record Category( String title, List<Page> pages ) {}

	private static final List<Page> _pages = new CopyOnWriteArrayList<>();

	private ERXControlPages() {}

	/**
	 * Registers a page
	 *
	 * @throws IllegalArgumentException if a page with the same name is already registered
	 */
	public static synchronized void register( final Page page ) {
		for( final Page existing : _pages ) {
			if( existing.name().equals( page.name() ) ) {
				throw new IllegalArgumentException( "A control panel page named '%s' is already registered (%s), so %s can't be".formatted( page.name(), existing.component().getName(), page.component().getName() ) );
			}
		}

		_pages.add( page );
	}

	/**
	 * @return Every registered page, in the order they were registered
	 */
	public static List<Page> pages() {
		return List.copyOf( _pages );
	}

	/**
	 * @return The page with the given name, null if there's none
	 */
	public static Page page( final String name ) {
		for( final Page page : _pages ) {
			if( page.name().equals( name ) ) {
				return page;
			}
		}

		return null;
	}

	/**
	 * @return The categories, {@link #FRAMEWORK_CATEGORY} first, then in the order they were first registered
	 */
	public static List<Category> categories() {
		final Map<String, List<Page>> pagesByCategory = new LinkedHashMap<>();
		pagesByCategory.put( FRAMEWORK_CATEGORY, new ArrayList<>() );

		for( final Page page : _pages ) {
			pagesByCategory.computeIfAbsent( page.category(), c -> new ArrayList<>() ).add( page );
		}

		final List<Category> categories = new ArrayList<>();

		pagesByCategory.forEach( ( title, pages ) -> {
			if( !pages.isEmpty() ) {
				categories.add( new Category( title, List.copyOf( pages ) ) );
			}
		} );

		return categories;
	}
}
