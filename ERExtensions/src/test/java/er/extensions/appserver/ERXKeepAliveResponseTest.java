package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
public class ERXKeepAliveResponseTest {

	private static byte[] read( final InputStream stream, final int count ) throws Exception {
		final byte[] bytes = new byte[count];

		for( int i = 0; i < count; i++ ) {
			final int b = stream.read();

			if( b == -1 ) {
				throw new IllegalStateException( "Stream ended after " + i + " bytes" );
			}

			bytes[i] = (byte)b;
		}

		return bytes;
	}

	@Test
	public void bytesAreReadUnsigned() throws Exception {
		final ERXKeepAliveResponse response = new ERXKeepAliveResponse();
		final byte[] data = "þú ÿ".getBytes( StandardCharsets.UTF_8 );
		response.push( data );
		assertArrayEquals( data, CompletableFuture.supplyAsync( () -> { try { return read( response.contentInputStream(), data.length ); } catch( Exception e ) { throw new RuntimeException( e ); } } ).get( 5, TimeUnit.SECONDS ) );
	}

	@Test
	public void itemsPushedTogetherAreAllDelivered() throws Exception {
		final ERXKeepAliveResponse response = new ERXKeepAliveResponse();
		response.push( new byte[] { 'a', 'b' } );
		response.push( new byte[] { 'c' } );
		assertArrayEquals( new byte[] { 'a', 'b', 'c' }, CompletableFuture.supplyAsync( () -> { try { return read( response.contentInputStream(), 3 ); } catch( Exception e ) { throw new RuntimeException( e ); } } ).get( 5, TimeUnit.SECONDS ) );
	}

	@Test
	public void resetEndsTheStream() throws Exception {
		final ERXKeepAliveResponse response = new ERXKeepAliveResponse();
		final InputStream stream = response.contentInputStream();
		final CompletableFuture<Integer> reading = CompletableFuture.supplyAsync( () -> { try { return stream.read(); } catch( Exception e ) { throw new RuntimeException( e ); } } );
		Thread.sleep( 100 );
		response.push( new byte[] { 'x' } );
		assertEquals( 'x', reading.get( 5, TimeUnit.SECONDS ) );
		response.push( new byte[] { 'y' } );
		response.reset();
		assertEquals( -1, stream.read() );
	}
}
