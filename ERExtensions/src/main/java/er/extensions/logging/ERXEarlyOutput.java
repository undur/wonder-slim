package er.extensions.logging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Keeps what's written to the console before WebObjects redirects it to the {@code WOOutputPath} file, and writes it at
 * the start of that file once it has.
 *
 * WebObjects points {@code System.out} and {@code System.err} at the file while the application is constructed, first
 * renaming an existing file at that path to {@code <path>.<timestamp>}. Everything written before goes to the process's
 * original output, which wotaskd doesn't keep: what's logged while the configuration is composed, by plugins before
 * construction, and by WebObjects itself while it starts. Opening the file earlier ourselves wouldn't help, since
 * WebObjects would rename it away, early lines and all.
 *
 * {@link #start()}, from {@code ERXApplication}'s static initializer, has the console streams keep a copy of what's
 * written, as well as writing it as before, up to {@link #LIMIT}. {@link #finish()}, from {@code ERXApplication}'s
 * constructor, writes the copy to the new {@code System.out} if WebObjects redirected it, and otherwise puts the
 * original streams back and discards it.
 */
public final class ERXEarlyOutput {

	/**
	 * How much is kept, at most: startup output is a few kilobytes, this is plenty
	 */
	static final int LIMIT = 1024 * 1024;

	private static final Object LOCK = new Object();

	private static PrintStream _originalOut;
	private static PrintStream _originalErr;

	/**
	 * The streams installed in place of the originals, so {@link #finish()} can tell whether they were replaced
	 */
	private static PrintStream _keepingOut;
	private static PrintStream _keepingErr;

	/**
	 * What was written, null when not keeping
	 */
	private static ByteArrayOutputStream _kept;

	private static boolean _truncated;

	private ERXEarlyOutput() {}

	/**
	 * Starts keeping what's written to {@code System.out} and {@code System.err}. Does nothing if already keeping.
	 */
	public static void start() {
		synchronized( LOCK ) {
			if( _kept != null ) {
				return;
			}

			_kept = new ByteArrayOutputStream();
			_truncated = false;
			_originalOut = System.out;
			_originalErr = System.err;
			_keepingOut = new PrintStream( new KeepingStream( _originalOut ), true, _originalOut.charset() );
			_keepingErr = new PrintStream( new KeepingStream( _originalErr ), true, _originalErr.charset() );
			System.setOut( _keepingOut );
			System.setErr( _keepingErr );
		}
	}

	/**
	 * Stops keeping. If {@code System.out} was redirected to a file since {@link #start()}, writes what was kept to it,
	 * first thing; otherwise puts the original streams back.
	 */
	public static void finish() {
		final byte[] kept;
		final boolean truncated;
		final PrintStream out = System.out;

		synchronized( LOCK ) {
			if( _kept == null ) {
				return;
			}

			kept = _kept.toByteArray();
			truncated = _truncated;
			_kept = null;

			// Not redirected: the original streams go back, and what was kept was written to them already
			if( System.out == _keepingOut ) {
				System.setOut( _originalOut );
			}

			if( System.err == _keepingErr ) {
				System.setErr( _originalErr );
			}
		}

		if( out != _keepingOut && !isDiscarding( out ) && kept.length > 0 ) {
			synchronized( out ) {
				out.println( "==== Written before WOOutputPath took effect ====" );
				out.write( kept, 0, kept.length );

				if( truncated ) {
					out.println( "(more was written than kept)" );
				}

				out.println( "==== End of what was written before WOOutputPath took effect ====" );
				out.flush();
			}
		}
	}

	/**
	 * @return true for the stream WebObjects installs for a {@code WOOutputPath} of {@code /dev/null}
	 */
	private static boolean isDiscarding( final PrintStream stream ) {
		return stream.getClass().getName().endsWith( "_DevNullPrintStream" );
	}

	/**
	 * Keeps a copy of what's written, up to {@link ERXEarlyOutput#LIMIT}, while writing it on
	 */
	private static final class KeepingStream extends OutputStream {

		private final OutputStream _target;

		KeepingStream( final OutputStream target ) {
			_target = target;
		}

		@Override
		public void write( final int b ) throws IOException {
			_target.write( b );
			keep( new byte[] { (byte)b }, 0, 1 );
		}

		@Override
		public void write( final byte[] bytes, final int offset, final int length ) throws IOException {
			_target.write( bytes, offset, length );
			keep( bytes, offset, length );
		}

		@Override
		public void flush() throws IOException {
			_target.flush();
		}

		private static void keep( final byte[] bytes, final int offset, final int length ) {
			synchronized( LOCK ) {
				if( _kept == null ) {
					return;
				}

				final int room = LIMIT - _kept.size();

				if( length > room ) {
					_truncated = true;
				}

				if( room > 0 ) {
					_kept.write( bytes, offset, Math.min( length, room ) );
				}
			}
		}
	}
}
