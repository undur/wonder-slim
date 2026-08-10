/**
 * Development-mode machinery — <strong>internal framework logic, not public API.</strong>
 *
 * <p>These types exist to support the development loop: the HTTP surface that lets external tools
 * and AI agents drive a running dev instance (console log, code eval, rendered runtime problems),
 * plus the app's registration with the Parslips dev server and the stop-the-previous-instance
 * handshake. They are wired up by {@code ERXApplication} only when the app runs in development
 * mode, and are not meant to be depended on by application code — unlike {@code er.extensions.appserver},
 * which carries the framework's public application-level API.
 *
 * <ul>
 *   <li>{@link er.extensions.dev.ERXConsoleCapture} / {@link er.extensions.dev.ERXConsoleLogRequestHandler}
 *       — capture the app's console output and serve it over HTTP ({@code …woa/log}).</li>
 *   <li>{@link er.extensions.dev.ERXEvalRequestHandler} — evaluate a Java snippet inside the running
 *       JVM ({@code …woa/eval}); loopback-only.</li>
 *   <li>{@link er.extensions.dev.ERXRuntimeProblemsRequestHandler} — the rendered binding-error boxes
 *       as data ({@code …woa/problems}).</li>
 *   <li>{@link er.extensions.dev.ERXDevServerRegistration} — announce this instance to the Parslips
 *       dev server so tools can discover its port.</li>
 *   <li>{@link er.extensions.dev.ERXDevelopmentInstanceStopper} — stop a previous dev instance so a
 *       relaunch can take the port.</li>
 * </ul>
 *
 * The eval / runtime-problems endpoints share one engine with ng-objects (in {@code ng.dev}, from
 * ng-core), so both frameworks expose the same behavior; only the mount path differs.
 */
package er.extensions.dev;
