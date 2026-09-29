package er.extensions.foundation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ERXConfigurationManagerMaskingTest {

	@BeforeEach
	public void setSecrets() {
		System.setProperty( "er.test.masking.password", "hunter2-and-more" );
		System.setProperty( "er.test.masking.token", "abc" );
	}

	@AfterEach
	public void clearSecrets() {
		System.clearProperty( "er.test.masking.password" );
		System.clearProperty( "er.test.masking.token" );
	}

	@Test
	public void aSecretKeysValueIsMaskedEntirely() {
		assertEquals( "********", ERXConfigurationManager.maskedValue( "WOMonitorServicePassword", "anything" ) );
		assertEquals( "********", ERXConfigurationManager.maskedValue( "anthropic.apiKey", "anything" ) );
	}

	@Test
	public void aSecretInsideAnotherValueIsMasked() {
		assertEquals( "App -WOPort 1 -er.test.masking.password ******** -X y", ERXConfigurationManager.maskedValue( "sun.java.command", "App -WOPort 1 -er.test.masking.password hunter2-and-more -X y" ) );
	}

	@Test
	public void veryShortSecretsDontMaskOrdinaryText() {
		assertEquals( "abcdef", ERXConfigurationManager.maskedValue( "some.key", "abcdef" ) );
	}

	@Test
	public void anOrdinaryValueIsUnchanged() {
		assertEquals( "47831", ERXConfigurationManager.maskedValue( "WOPort", "47831" ) );
		assertNull( ERXConfigurationManager.maskedValue( "WOPort", null ) );
	}
}
