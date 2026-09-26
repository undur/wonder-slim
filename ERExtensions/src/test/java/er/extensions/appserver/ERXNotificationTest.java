package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class ERXNotificationTest {

	private static final ERXNotification NOTIFICATION = ERXNotification.ApplicationWillTerminateNotification;

	@Test
	public void aLambdaObserverSurvivesGarbageCollectionUntilRemoved() throws InterruptedException {
		final AtomicInteger calls = new AtomicInteger();
		final ERXNotification.Registration registration = NOTIFICATION.addObserver( _ -> calls.incrementAndGet() );

		// NSNotificationCenter holds observers weakly; the registration must keep the lambda's observer alive
		for( int i = 0; i < 5; i++ ) {
			System.gc();
			Thread.sleep( 20 );
		}

		NOTIFICATION.postNotification( null );
		assertEquals( 1, calls.get() );

		registration.remove();
		NOTIFICATION.postNotification( null );
		assertEquals( 1, calls.get(), "a removed observer no longer fires" );

		registration.remove(); // removing again does nothing
	}

	@Test
	public void theSameLambdaRegisteredTwiceIsTwoObservers() {
		final AtomicInteger calls = new AtomicInteger();
		final java.util.function.Consumer<com.webobjects.foundation.NSNotification> consumer = _ -> calls.incrementAndGet();

		try( ERXNotification.Registration first = NOTIFICATION.addObserver( consumer ) ) {
			final ERXNotification.Registration second = NOTIFICATION.addObserver( consumer );
			NOTIFICATION.postNotification( null );
			assertEquals( 2, calls.get() );

			second.remove();
			NOTIFICATION.postNotification( null );
			assertEquals( 3, calls.get(), "removing one leaves the other registered" );
		}

		NOTIFICATION.postNotification( null );
		assertEquals( 3, calls.get() );
	}
}
