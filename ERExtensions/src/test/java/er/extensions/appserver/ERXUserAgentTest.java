package er.extensions.appserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import er.extensions.appserver.ERXUserAgent.Family;
import er.extensions.appserver.ERXUserAgent.Platform;

public class ERXUserAgentTest {

	private static ERXUserAgent ua( final String header ) {
		return ERXUserAgent.parse( header );
	}

	@Test
	public void desktopBrowsers() {
		assertEquals( Family.CHROME, ua( "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36" ).family() );
		assertEquals( Family.SAFARI, ua( "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15" ).family() );
		assertEquals( Family.FIREFOX, ua( "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0" ).family() );
		assertEquals( Family.EDGE, ua( "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/129.0.0.0" ).family() );
		assertEquals( Family.OPERA, ua( "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 OPR/114.0.0.0" ).family() );
		assertFalse( ua( "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36" ).isMobile() );
		assertFalse( ua( "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36" ).isBot() );
	}

	@Test
	public void mobileBrowsers() {
		final ERXUserAgent iphone = ua( "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1" );
		assertEquals( Family.SAFARI, iphone.family() );
		assertTrue( iphone.isMobile() );

		final ERXUserAgent chromeOnIOS = ua( "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/129.0.6668.69 Mobile/15E148 Safari/604.1" );
		assertEquals( Family.CHROME, chromeOnIOS.family() );

		final ERXUserAgent android = ua( "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36" );
		assertEquals( Family.CHROME, android.family() );
		assertTrue( android.isMobile() );
	}

	@Test
	public void majorVersionAndPlatform() {
		final ERXUserAgent chromeMac = ua( "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36" );
		assertEquals( 129, chromeMac.majorVersion() );
		assertEquals( Platform.MACOS, chromeMac.platform() );

		final ERXUserAgent safariMac = ua( "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15" );
		assertEquals( 18, safariMac.majorVersion() );

		final ERXUserAgent firefoxWindows = ua( "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0" );
		assertEquals( 131, firefoxWindows.majorVersion() );
		assertEquals( Platform.WINDOWS, firefoxWindows.platform() );

		final ERXUserAgent edge = ua( "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36 Edg/128.0.0.0" );
		assertEquals( 128, edge.majorVersion(), "Edge's own version, not the Chrome it announces" );

		final ERXUserAgent iphone = ua( "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1" );
		assertEquals( Platform.IOS, iphone.platform() );
		assertEquals( 18, iphone.majorVersion() );

		final ERXUserAgent chromeOnIOS = ua( "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/129.0.6668.69 Mobile/15E148 Safari/604.1" );
		assertEquals( 129, chromeOnIOS.majorVersion() );

		final ERXUserAgent android = ua( "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36" );
		assertEquals( Platform.ANDROID, android.platform() );

		assertEquals( Platform.LINUX, ua( "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0" ).platform() );
		assertEquals( null, ua( "curl/8.7.1" ).majorVersion() );
		assertEquals( Platform.OTHER, ua( "curl/8.7.1" ).platform() );
		assertEquals( null, ua( null ).majorVersion() );
	}

	@Test
	public void bots() {
		assertTrue( ua( "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)" ).isBot() );
		assertTrue( ua( "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm) Chrome/116.0.1938.76 Safari/537.36" ).isBot() );
		assertTrue( ua( "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; GPTBot/1.2; +https://openai.com/gptbot)" ).isBot() );
		assertTrue( ua( "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; ClaudeBot/1.0; +claudebot@anthropic.com)" ).isBot() );
		assertTrue( ua( "Mozilla/5.0 (Linux; Android 5.0) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36 (compatible; Bytespider; spider-feedback@bytedance.com)" ).isBot() );
		assertTrue( ua( "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)" ).isBot() );
		assertTrue( ua( "curl/8.7.1" ).isBot() );
		assertTrue( ua( "python-requests/2.32.3" ).isBot() );
		assertTrue( ua( "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/129.0.0.0 Safari/537.36" ).isBot() );
		assertTrue( ua( "WhatsApp/2.23.20.0" ).isBot() );
		assertTrue( ua( "Apache-HttpClient/5.2.1 (Java/21.0.4)" ).isBot() );
		assertTrue( ua( "Java-http-client/21.0.4" ).isBot() );
		assertTrue( ua( "axios/1.7.7" ).isBot() );
		assertTrue( ua( "node-fetch" ).isBot() );
		assertTrue( ua( "Scrapy/2.11.2 (+https://scrapy.org)" ).isBot() );
		assertTrue( ua( "Mozilla/5.0 (compatible; Barkrowler/0.9; +https://babbar.tech/crawler)" ).isBot() );
		assertTrue( ua( "python-httpx/0.27.2" ).isBot() );
		assertTrue( ua( "Python/3.12 aiohttp/3.10.5" ).isBot() );
		assertTrue( ua( null ).isBot() );
		assertFalse( ua( "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP2A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/129.0.6668.70 Mobile Safari/537.36 WhatsApp/2.24" ).isBot(), "WhatsApp's in-app browser is a person" );
		assertTrue( ua( "" ).isBot() );
		assertTrue( ua( "x" ).isBot() );
	}

	@Test
	public void applicationsCanAddBotMarkers() {
		final String header = "Mozilla/5.0 (compatible; SomeNewFetcher/1.0)";
		assertFalse( ua( header ).isBot() );
		ERXUserAgent.addBotMarkers( "SomeNewFetcher" );
		assertTrue( ua( header ).isBot() );
	}
}
