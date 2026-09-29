package er.extensions.logging;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.webobjects.appserver._private.WOCGIFormValues;

/**
 * Rewrites WebObjects' form value encoder ({@code WOCGIFormValues$Encoder}) to log through slf4j rather than the log4j
 * 1.x API, so WebObjects runs without a log4j API on the classpath. The encoder is the only part of the WebObjects
 * frameworks that calls log4j (NSLog's own log4j logger aside, which only loads if NSLog is pointed at it): without a
 * log4j API, the first URL with query parameters would fail with a {@code NoClassDefFoundError}.
 *
 * {@link #install()} reads the encoder's class file from WebObjects' jar, rewrites its logger, and defines the result in
 * WebObjects' own class loader and package, before WebObjects first uses the encoder (it creates it on first use). No
 * agent or JVM option is involved. The class is only rewritten if it uses log4j in the ways handled here; otherwise, or
 * if it's already loaded, it's left as it is.
 */
public final class ERXLog4jRewrite {

	private static final Logger logger = LoggerFactory.getLogger( ERXLog4jRewrite.class );

	/**
	 * The class rewritten
	 */
	static final String ENCODER = "com.webobjects.appserver._private.WOCGIFormValues$Encoder";

	private static final String LOG4J_PACKAGE = "org/apache/log4j/";

	private static final ClassDesc LOG4J_LOGGER = ClassDesc.of( "org.apache.log4j.Logger" );
	private static final ClassDesc LOG4J_CATEGORY = ClassDesc.of( "org.apache.log4j.Category" );
	private static final ClassDesc SLF4J_LOGGER = ClassDesc.of( "org.slf4j.Logger" );
	private static final ClassDesc SLF4J_LOGGER_FACTORY = ClassDesc.of( "org.slf4j.LoggerFactory" );
	private static final ClassDesc THROWABLE = ClassDesc.of( "java.lang.Throwable" );

	/**
	 * log4j's logging methods, with the name of slf4j's counterpart
	 */
	private static final Map<String, String> LEVEL_METHODS = Map.of( "trace", "trace", "debug", "debug", "info", "info", "warn", "warn", "error", "error", "fatal", "error" );

	private ERXLog4jRewrite() {}

	/**
	 * Thrown when the class uses log4j in a way the rewrite doesn't handle
	 */
	static final class UnsupportedUseException extends RuntimeException {

		UnsupportedUseException( final String message ) {
			super( message );
		}
	}

	/**
	 * Rewrites the encoder and defines it, unless it's loaded already or can't be rewritten
	 *
	 * @return true if the rewritten encoder is defined
	 */
	public static boolean install() {
		final byte[] original;

		try( final InputStream stream = WOCGIFormValues.class.getClassLoader().getResourceAsStream( ENCODER.replace( '.', '/' ) + ".class" ) ) {
			if( stream == null ) {
				logger.warn( "Not rewriting WebObjects' form value encoder to log through slf4j: its class file wasn't found" );
				return false;
			}

			original = stream.readAllBytes();
		}
		catch( IOException e ) {
			logger.warn( "Not rewriting WebObjects' form value encoder to log through slf4j: its class file couldn't be read", e );
			return false;
		}

		if( !usesLog4j( original ) ) {
			return false;
		}

		final byte[] rewritten;

		try {
			rewritten = rewrite( original );
		}
		catch( UnsupportedUseException e ) {
			logger.warn( "Not rewriting WebObjects' form value encoder to log through slf4j: {}. It needs a log4j API on the classpath.", e.getMessage() );
			return false;
		}

		try {
			MethodHandles.privateLookupIn( WOCGIFormValues.class, MethodHandles.lookup() ).defineClass( rewritten );
			return true;
		}
		catch( LinkageError | IllegalAccessException e ) {
			logger.warn( "Not rewriting WebObjects' form value encoder to log through slf4j: it's loaded already ({}). It needs a log4j API on the classpath.", e.toString() );
			return false;
		}
	}

	/**
	 * @return true if the class file refers to log4j
	 */
	static boolean usesLog4j( final byte[] classFile ) {
		for( final PoolEntry entry : ClassFile.of().parse( classFile ).constantPool() ) {
			if( entry instanceof Utf8Entry utf8 && utf8.stringValue().contains( LOG4J_PACKAGE ) ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * @return The class file, with its log4j logger replaced by an slf4j one
	 *
	 * @throws UnsupportedUseException if the class uses log4j in a way not handled here
	 */
	static byte[] rewrite( final byte[] classFile ) {
		// A new constant pool, since the original's log4j entries would otherwise be carried over unused
		final ClassFile classFiles = ClassFile.of( ClassFile.ConstantPoolSharingOption.NEW_POOL );
		final ClassModel model = classFiles.parse( classFile );

		final ClassTransform fields = ( builder, element ) -> {
			if( element instanceof FieldModel field && field.fieldTypeSymbol().equals( LOG4J_LOGGER ) ) {
				builder.withField( field.fieldName().stringValue(), SLF4J_LOGGER, field.flags().flagsMask() );
			}
			else {
				builder.with( element );
			}
		};

		final ClassTransform transform = fields.andThen( ClassTransform.transformingMethodBodies( ERXLog4jRewrite::rewriteInstruction ) );
		final byte[] rewritten = classFiles.transformClass( model, transform );

		if( usesLog4j( rewritten ) ) {
			throw new UnsupportedUseException( "it uses log4j in ways other than the rewrite handles" );
		}

		return rewritten;
	}

	/**
	 * Replaces an instruction using log4j's logger with the slf4j equivalent
	 */
	private static void rewriteInstruction( final CodeBuilder builder, final CodeElement element ) {

		// Debug information naming the logger's type goes: it's optional
		if( element instanceof LocalVariable local && isLog4j( local.typeSymbol() ) ) {
			return;
		}

		if( element instanceof LocalVariableType ) {
			return;
		}

		if( element instanceof FieldInstruction field && field.typeSymbol().equals( LOG4J_LOGGER ) ) {
			builder.fieldAccess( field.opcode(), field.owner().asSymbol(), field.name().stringValue(), SLF4J_LOGGER );
			return;
		}

		if( element instanceof InvokeInstruction invoke && isLog4j( invoke.owner().asSymbol() ) ) {
			rewriteInvocation( builder, invoke );
			return;
		}

		builder.with( element );
	}

	private static void rewriteInvocation( final CodeBuilder builder, final InvokeInstruction invoke ) {
		final String name = invoke.name().stringValue();
		final MethodTypeDesc type = invoke.typeSymbol();

		// Logger.getLogger( Class ) and Logger.getLogger( String )
		if( invoke.opcode() == Opcode.INVOKESTATIC && name.equals( "getLogger" ) && type.parameterCount() == 1 && type.returnType().equals( LOG4J_LOGGER ) ) {
			builder.invokestatic( SLF4J_LOGGER_FACTORY, "getLogger", MethodTypeDesc.of( SLF4J_LOGGER, type.parameterType( 0 ) ) );
			return;
		}

		if( invoke.opcode() == Opcode.INVOKEVIRTUAL ) {

			// isDebugEnabled(), isInfoEnabled(), isTraceEnabled()
			if( name.startsWith( "is" ) && name.endsWith( "Enabled" ) && type.parameterCount() == 0 && LEVEL_METHODS.containsKey( level( name ) ) ) {
				builder.invokeinterface( SLF4J_LOGGER, "is" + capitalized( LEVEL_METHODS.get( level( name ) ) ) + "Enabled", MethodTypeDesc.of( ConstantDescs.CD_boolean ) );
				return;
			}

			// debug( Object ) and debug( Object, Throwable ), and the other levels': slf4j logs a String
			if( LEVEL_METHODS.containsKey( name ) ) {
				final String slf4jName = LEVEL_METHODS.get( name );

				if( type.equals( MethodTypeDesc.of( ConstantDescs.CD_void, ConstantDescs.CD_Object ) ) ) {
					builder.invokestatic( ConstantDescs.CD_String, "valueOf", MethodTypeDesc.of( ConstantDescs.CD_String, ConstantDescs.CD_Object ) );
					builder.invokeinterface( SLF4J_LOGGER, slf4jName, MethodTypeDesc.of( ConstantDescs.CD_void, ConstantDescs.CD_String ) );
					return;
				}

				if( type.equals( MethodTypeDesc.of( ConstantDescs.CD_void, ConstantDescs.CD_Object, THROWABLE ) ) ) {
					final int throwable = builder.allocateLocal( TypeKind.REFERENCE );
					builder.astore( throwable );
					builder.invokestatic( ConstantDescs.CD_String, "valueOf", MethodTypeDesc.of( ConstantDescs.CD_String, ConstantDescs.CD_Object ) );
					builder.aload( throwable );
					builder.invokeinterface( SLF4J_LOGGER, slf4jName, MethodTypeDesc.of( ConstantDescs.CD_void, ConstantDescs.CD_String, THROWABLE ) );
					return;
				}
			}
		}

		throw new UnsupportedUseException( "it calls %s.%s%s".formatted( invoke.owner().asInternalName(), name, type.descriptorString() ) );
	}

	private static boolean isLog4j( final ClassDesc type ) {
		return type.equals( LOG4J_LOGGER ) || type.equals( LOG4J_CATEGORY );
	}

	/**
	 * @return The level an is…Enabled() method asks about: "debug" for isDebugEnabled
	 */
	private static String level( final String enabledMethod ) {
		return enabledMethod.substring( 2, enabledMethod.length() - "Enabled".length() ).toLowerCase();
	}

	private static String capitalized( final String string ) {
		return Character.toUpperCase( string.charAt( 0 ) ) + string.substring( 1 );
	}
}
