// Range-request harness. Plays the playground's /video page (a 20-second WebM served by the resource request handler)
// and seeks into it. A browser plays and seeks media with range requests (Range: bytes=…), so this asserts that the
// video's requests are answered with 206 Partial Content, that a seek lands where it should, and that no request fetched
// the whole file. Reports PASS/FAIL per check; exit code 1 if anything failed.
//
// Run:  BASE=http://localhost:<port> node examples/video-range.mjs      (BROWSER=firefox for Firefox)
import { chromium, firefox } from '/Users/hugi/git/wonder-slim/tools/playwright-bridge/node_modules/playwright/index.mjs';

const BASE = process.env.BASE || 'http://localhost:1200';

let pass = 0, fail = 0;
const ok = ( name, condition, detail ) => {
	if( condition ) { pass++; console.log( `  PASS  ${name}` ); }
	else { fail++; console.log( `  FAIL  ${name}${detail ? '  ' + detail : ''}` ); }
};

const browser = await ( process.env.BROWSER === 'firefox' ? firefox : chromium ).launch();
const page = await ( await browser.newContext() ).newPage();

const videoResponses = [];
page.on( 'response', r => {
	if( r.url().includes( '/video/range-test' ) ) {
		videoResponses.push( { status: r.status(), range: r.request().headers()[ 'range' ] || null, contentRange: r.headers()[ 'content-range' ] || null } );
	}
} );

await page.goto( BASE + '/video' );
await page.waitForFunction( () => document.getElementById( 'video' ).readyState >= 1, null, { timeout: 15000 } );

const duration = await page.evaluate( () => document.getElementById( 'video' ).duration );
ok( 'the video has its full duration (the file is indexed and readable)', Math.abs( duration - 20 ) < 0.5, `duration=${duration}` );

const seekedTo = await page.evaluate( () => new Promise( resolve => {
	const video = document.getElementById( 'video' );
	video.muted = true;
	video.addEventListener( 'seeked', () => resolve( video.currentTime ), { once: true } );
	video.play().catch( () => {} );
	video.currentTime = 15;
} ) );
ok( 'a seek to 15 s lands there', Math.abs( seekedTo - 15 ) < 1, `currentTime=${seekedTo}` );

await page.waitForTimeout( 1000 );
const partial = videoResponses.filter( r => r.status === 206 );
ok( 'the video was served with 206 Partial Content', partial.length > 0, JSON.stringify( videoResponses ) );
ok( 'every 206 names its range', partial.every( r => /^bytes \d+-\d+\/\d+$/.test( r.contentRange || '' ) ), JSON.stringify( partial ) );
ok( 'no request for the video got the whole file (200)', !videoResponses.some( r => r.status === 200 ), JSON.stringify( videoResponses ) );

await browser.close();
console.log( `\n================  ${pass} passed, ${fail} failed  ================` );
process.exit( fail ? 1 : 0 );
