package er.extensions.dev.ng;

import java.util.ArrayList;
import java.util.List;

/*
 * TEMPORARY facade over ng-core's ng.dev.NGRuntimeProblems, living here so wonder-slim can
 * release without depending on an unreleased ng-core. Delete this package and re-point the
 * er.extensions.dev handlers at ng.dev once ng-core >= 0.1.2 is released (and parsley is pinned
 * to a release that depends on it). Do not extend, do not reference from application code.
 */

/**
 * Reflective facade over the shared runtime-problems store ({@code ng.dev.NGRuntimeProblems}).
 * The store must be the REAL ng-core class - Parsley's error element records into it, and both
 * frameworks' dev endpoints read it - so a copied store would silently split the world in two:
 * Parsley writing ng-core's buffer while our endpoint reads an always-empty local one. Instead
 * this facade calls the real class reflectively when it is on the classpath (any workspace/dev
 * setup, where parsley's SNAPSHOT chain brings ng-core along) and degrades to empty results when
 * it isn't (an app built against the released, ng-free stack - where nothing records problems
 * anyway, since released parsley predates the recording).
 */
public final class NGRuntimeProblems {

	/** Mirrors {@code ng.dev.NGRuntimeProblems.Problem}. */
	public record Problem( long epochMillis, String kind, String element, String message ) {}

	private static final Class<?> _store = locateStore();

	private NGRuntimeProblems() {}

	private static Class<?> locateStore() {
		try {
			return Class.forName( "ng.dev.NGRuntimeProblems" );
		}
		catch( ClassNotFoundException e ) {
			return null;
		}
	}

	/** @return true when the real ng-core store is on the classpath */
	public static boolean storePresent() {
		return _store != null;
	}

	public static List<Problem> snapshot( final String contains, final int tail ) {
		if( _store == null ) {
			return List.of();
		}
		try {
			final List<?> problems = (List<?>)_store.getMethod( "snapshot", String.class, int.class ).invoke( null, contains, tail );
			final List<Problem> result = new ArrayList<>( problems.size() );
			for( final Object problem : problems ) {
				final Class<?> problemClass = problem.getClass();
				result.add( new Problem(
						(Long)problemClass.getMethod( "epochMillis" ).invoke( problem ),
						(String)problemClass.getMethod( "kind" ).invoke( problem ),
						(String)problemClass.getMethod( "element" ).invoke( problem ),
						(String)problemClass.getMethod( "message" ).invoke( problem ) ) );
			}
			return result;
		}
		catch( Exception e ) {
			// A dev-mode convenience must never break on reflection details - degrade to empty.
			return List.of();
		}
	}

	public static void clear() {
		if( _store == null ) {
			return;
		}
		try {
			_store.getMethod( "clear" ).invoke( null );
		}
		catch( Exception e ) {
			// Same degrade-to-noop contract as snapshot().
		}
	}
}
