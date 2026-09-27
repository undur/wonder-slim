package er.extensions;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Properties that configured features wonder-slim no longer has, mostly Project Wonder's, reported at startup so an
 * application moving over knows which of its settings no longer do anything, and why.
 */

public class ERXObsoleteProperties {

	private static final Logger log = LoggerFactory.getLogger( ERXObsoleteProperties.class );

	/**
	 * A property that configured a removed feature, and why it's gone. A {@code *} in the key matches any text.
	 */
	private record ObsoleteProperty( String key, String message ) {

		boolean matches( final String propertyName ) {
			if( key.contains( "*" ) ) {
				return propertyName.matches( Pattern.quote( key ).replace( "*", "\\E.+\\Q" ) );
			}

			return propertyName.equals( key );
		}
	}

	private static final String EOF_SUPPORT = "Part of Project Wonder's EOF support, which wonder-slim doesn't include.";
	private static final String WONDER_FEATURE = "Configures a Project Wonder feature that wonder-slim doesn't include.";
	private static final String URL_REWRITING = "URL rewriting by pattern has been removed. Short URLs (er.extensions.ERXApplication.shortURLs, on by default) remove the adaptor prefix from generated URLs and accept them inbound. Serving an application beneath a path of its own is not supported at present.";
	private static final String LOCALIZER = "ERXLocalizer has been removed. Locale-aware formatting is configured with ERXLocale.setApplicationLocale() or ERXSession.setLocale(); see er.extensions.appserver.ERXLocale.";
	private static final String BROWSER = "The browser package has been removed. User agent information is available from er.extensions.appserver.ERXUserAgent.";
	private static final String SSL = "The SSL direct-connect adaptor has been removed. TLS is the job of the web server in front of the application.";
	private static final String CRYPTO = "Wonder's crypto package has been removed. Use the JDK's javax.crypto or a dedicated library.";
	private static final String ADMIN = "Wonder's administrative direct actions are gone. Administration lives in the ERXControl module, protected by WOMonitorServicePassword.";
	private static final String PATCHER = "ERXPatcher's element patches have been removed; the dynamic elements render standard HTML.";
	private static final String MESSAGE_ENCODING = "ERXMessageEncoding has been removed.";
	private static final String JSON = "Ajax's JSON-RPC bridge has been removed.";
	private static final String OGNL = "WOOgnl has been removed. Templates are parsed by Parsley, which ERExtensions enables by default.";

	/**
	 * Properties that configured removed features, reported at startup (see {@link #printObsoleteProperties()}). Every
	 * key is listed explicitly; a {@code *} stands for the part of a key Project Wonder built at runtime from a name
	 * (a language, a model, an entity). The message is what a migrating application reads, so where a feature has a
	 * replacement, the message names it. Add a line when a feature with configuration is removed.
	 */
	private static final List<ObsoleteProperty> OBSOLETE_PROPERTIES = List.of(
		// URL rewriting
		new ObsoleteProperty( "er.extensions.ERXApplication.replaceApplicationPath.pattern", URL_REWRITING ),
		new ObsoleteProperty( "er.extensions.ERXApplication.replaceApplicationPath.replace", URL_REWRITING ),

		// ERXLocalizer
		new ObsoleteProperty( "er.extensions.*.hasLocalization", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizationEditor.endoding", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizer.*.locale", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizer.*.plurifyRules", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizer.*.singularifyRules", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizer.availableLanguages", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizer.defaultLanguage", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizer.fallbackToDefaultLanguage", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizer.fileNamesToWatch", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizer.frameworkSearchPath", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizer.isLocalizationEnabled", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizer.nonPluralFormClassName", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizer.pluralFormClassName", LOCALIZER ),
		new ObsoleteProperty( "er.extensions.ERXLocalizer.useLocalizedFormatters", LOCALIZER ),

		// The browser package
		new ObsoleteProperty( "er.extensions.ERXBrowserFactory.BrowserClassName", BROWSER ),
		new ObsoleteProperty( "er.extensions.ERXBrowserFactory.FactoryClassName", BROWSER ),

		// The SSL adaptor
		new ObsoleteProperty( "er.extensions.ERXApplication.ssl.enabled", SSL ),
		new ObsoleteProperty( "er.extensions.ERXApplication.ssl.host", SSL ),
		new ObsoleteProperty( "er.extensions.ERXApplication.ssl.port", SSL ),

		// Crypto
		new ObsoleteProperty( "er.extensions.ERXAESCipherKey", CRYPTO ),
		new ObsoleteProperty( "er.extensions.ERXBlowfishCipherKey", CRYPTO ),
		new ObsoleteProperty( "er.extensions.ERXCrypto.crypter.*", CRYPTO ),
		new ObsoleteProperty( "er.extensions.ERXCrypto.crypters", CRYPTO ),
		new ObsoleteProperty( "er.extensions.ERXCrypto.default", CRYPTO ),
		new ObsoleteProperty( "er.extensions.ERXKeyStoreBlowfishCrypter.keyAlias", CRYPTO ),
		new ObsoleteProperty( "er.extensions.ERXKeyStoreBlowfishCrypter.keyPassword", CRYPTO ),
		new ObsoleteProperty( "er.extensions.ERXKeyStoreBlowfishCrypter.keystorePassword", CRYPTO ),
		new ObsoleteProperty( "er.extensions.ERXKeyStoreBlowfishCrypter.keystorePath", CRYPTO ),
		new ObsoleteProperty( "ERBlowfishCipherKey", CRYPTO ),

		// Administrative direct actions
		new ObsoleteProperty( "er.extensions.ERXDirectAction.ChangeSystemPropertyPassword", ADMIN ),
		new ObsoleteProperty( "er.extensions.ERXFlushComponentCachePassword", ADMIN ),
		new ObsoleteProperty( "er.extensions.ERXGCPassword", ADMIN ),
		new ObsoleteProperty( "er.extensions.ERXJUnitPassword", ADMIN ),
		new ObsoleteProperty( "er.extensions.ERXLog4JPassword", ADMIN ),
		new ObsoleteProperty( "er.extensions.ERXRemoteShellPassword", ADMIN ),

		// ERXPatcher
		new ObsoleteProperty( "er.extensions.components._private.ERXSubmitButton.useIEFix", PATCHER ),
		new ObsoleteProperty( "er.extensions.ERXPatcher.cleanupXHTML", PATCHER ),
		new ObsoleteProperty( "er.extensions.ERXPatcher.suppressValueBindingSlow", PATCHER ),
		new ObsoleteProperty( "er.extensions.foundation.ERXPatcher.DynamicElementsPatches.appendComponentIdentifier", PATCHER ),
		new ObsoleteProperty( "er.extensions.foundation.ERXPatcher.DynamicElementsPatches.Javascript.removeLanguageAttribute", PATCHER ),
		new ObsoleteProperty( "er.extensions.WOSwitchComponent.patch", PATCHER ),

		// ERXMessageEncoding
		new ObsoleteProperty( "er.extensions.ERXApplication.DefaultMessageEncoding", MESSAGE_ENCODING ),
		new ObsoleteProperty( "er.extensions.ERXMessageEncoding.Enabled", MESSAGE_ENCODING ),

		// Ajax's JSON-RPC bridge
		new ObsoleteProperty( "er.ajax.json.*.attributes", JSON ),
		new ObsoleteProperty( "er.ajax.json.*.canInsert", JSON ),
		new ObsoleteProperty( "er.ajax.json.*.relationships", JSON ),
		new ObsoleteProperty( "er.ajax.json.*.writableAttributes", JSON ),
		new ObsoleteProperty( "er.ajax.json.backtrackCacheSize", JSON ),
		new ObsoleteProperty( "er.ajax.json.EOEditingContextFactory", JSON ),
		new ObsoleteProperty( "er.ajax.json.globalBacktrackCacheSize", JSON ),

		// WOOgnl
		new ObsoleteProperty( "ognl.active", OGNL ),
		new ObsoleteProperty( "ognl.debugSupport", OGNL ),
		new ObsoleteProperty( "ognl.helperFunctions", OGNL ),
		new ObsoleteProperty( "ognl.inlineBindings", OGNL ),
		new ObsoleteProperty( "ognl.parserClassName", OGNL ),
		new ObsoleteProperty( "ognl.parseStandardTags", OGNL ),
		new ObsoleteProperty( "ognl.webobjects.WOAssociation.shouldThrowExceptions", OGNL ),

		// Other removed features
		new ObsoleteProperty( "er.extensions.ERXExpiringCache.reaperFrequency", "ERXExpiringCache has been removed." ),
		new ObsoleteProperty( "er.extensions.ERXJavaScript.hideInComment", "ERXJavaScript no longer renders script content, so there is nothing to hide in a comment." ),
		new ObsoleteProperty( "er.extensions.appserver.ajax.ERXAjaxSession.logPageReplacementCache", "Renamed to er.extensions.appserver.ajax.ERXAjaxSession.logPageCache." ),
		new ObsoleteProperty( "er.extensions.maxPageReplacementCacheSize", "The page cache is bounded by WOPageCacheSize (default 30) instead." ),
		new ObsoleteProperty( "er.extensions.overridePrivateCache", "The unified page cache always replaces WO's private caches." ),
		new ObsoleteProperty( "er.extensions.ERXApplication.developmentMode", "Development mode is no longer set by property. It's detected from how the application is launched (from a project in the IDE)." ),
		new ObsoleteProperty( "er.extensions.ERXApplication.memoryThreshold", "Replaced by er.extensions.ERXApplication.memoryStarvedThreshold." ),
		new ObsoleteProperty( "er.extensions.ERXApplication.rewriteDirectConnect", "Direct connect URL rewriting has been removed." ),
		new ObsoleteProperty( "er.extensions.ERXGracefulShutdown.Enabled", "ERXGracefulShutdownHook has been removed. Use ERXShutdownHook instead." ),
		new ObsoleteProperty( "er.extensions.ERXGracefulShutdown.SignalsToHandle", "ERXGracefulShutdownHook has been removed. Use ERXShutdownHook instead." ),
		new ObsoleteProperty( "er.extensions.erxloggerclass", "ERXLogger has been removed. wonder-slim logs through SLF4J." ),
		new ObsoleteProperty( "er.extensions.ERXNavigationManager.includeLabelSpanTag", "The navigation manager has been removed." ),
		new ObsoleteProperty( "er.extensions.ERXNavigationManager.localizeDisplayKeys", "The navigation manager has been removed." ),
		new ObsoleteProperty( "er.extensions.ERXNavigationManager.NavigationMenuFileName", "The navigation manager has been removed." ),
		new ObsoleteProperty( "er.extensions.ERXSession.autoAdjustTimeZone", "Automatic time zone adjustment has been removed." ),
		new ObsoleteProperty( "er.extensions.ERXStyleSheet.xhtml", "XHTML output has been removed; ERXStyleSheet renders HTML." ),
		new ObsoleteProperty( "er.extensions.ERXWOContext.forceRemoveApplicationNumber", "Removing the instance number from generated URLs has been removed. Short URLs (er.extensions.ERXApplication.shortURLs, on by default) handle the adaptor prefix and instance number." ),
		new ObsoleteProperty( "er.extensions.ERXWOResponseCache.Enabled", "The response cache has been removed." ),

		// Project Wonder features wonder-slim doesn't include
		new ObsoleteProperty( "_DisableClasspathReorder", WONDER_FEATURE ),
		new ObsoleteProperty( "ajax.google.maps.apiKey", WONDER_FEATURE ),
		new ObsoleteProperty( "er.ajax.compressed", WONDER_FEATURE ),
		new ObsoleteProperty( "er.component.clickToOpen", WONDER_FEATURE ),
		new ObsoleteProperty( "er.erxtensions.ERXTcpIp.IpPriority*", WONDER_FEATURE ),
		new ObsoleteProperty( "er.erxtensions.ERXTcpIp.NoIpAndNoNetwork", WONDER_FEATURE ),
		new ObsoleteProperty( "er.erxtensions.ERXTcpIp.UseThisIp", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.appserver.projectBundleLoading", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.components.ERXModernizr.modernizrFileName", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.components.ERXModernizr.modernizrFrameworkName", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXApplication.useSessionStoreDeadlockDetection", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXArrayChooser.includeUnmatchedValuesDefault", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXArrayChooser.localizeDisplayKeysDefault", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXArrayChooser.sortCaseInsensitive", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXArrayUtilities.ShouldRegisterOperators", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXComparisonSupport.fixAnyway", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXComponentActionRedirector.enabled", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXJobLoadBalancer.DefaultDeadTimeoutMillis", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXJobLoadBalancer.RootLocation", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXProperties.RetainDefaultsEnabled", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXRequest.BrowserFormValueEncodingOverrideEnabled", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXResourceManager.versionManager", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXResourceManager.versionManager.*", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXThreadStorage.logUsageOfProblematicInheritedValues", WONDER_FEATURE ),
		new ObsoleteProperty( "er.extensions.ERXThreadStorage.useInheritableThreadLocal", WONDER_FEATURE ),
		new ObsoleteProperty( "er.r2d2w.components.misc.R2DLinkButton.defaultClass", WONDER_FEATURE ),
		new ObsoleteProperty( "ERApplicationHostURL", WONDER_FEATURE ),
		new ObsoleteProperty( "ERApplicationName", WONDER_FEATURE ),
		new ObsoleteProperty( "ERApplicationNameSuffix", WONDER_FEATURE ),
		new ObsoleteProperty( "ERXDirectComponentAccessAllowed", WONDER_FEATURE ),
		new ObsoleteProperty( "ERXDocumentRoot", WONDER_FEATURE ),
		new ObsoleteProperty( "NSProperties.useLoadtimeAppSpecifics", WONDER_FEATURE ),
		new ObsoleteProperty( "wodka.a10.A10TcpIp.IpPriority*", WONDER_FEATURE ),
		new ObsoleteProperty( "wodka.a10.A10TcpIp.NoIpAndNoNetwork", WONDER_FEATURE ),
		new ObsoleteProperty( "wodka.a10.A10TcpIp.UseThisIp", WONDER_FEATURE ),

		// Project Wonder's EOF support
		new ObsoleteProperty( "dbConfigNameGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbConnectAdaptorGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbConnectDatabaseGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbConnectDriverGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbConnectHostNameGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbConnectionRecycleGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbConnectJDBCInfoGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbConnectPasswordGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbConnectPathGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbConnectPluginGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbConnectServerGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbConnectURLGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbConnectUserGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbDebugLevelGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbEOPrototypesEntityGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbLogPathGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbMaxCheckoutGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbMaxConnectionsGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbMinConnectionsGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "dbRemoveJdbc2InfoGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "EOPrototypesFileGLOBAL", EOF_SUPPORT ),
		new ObsoleteProperty( "EOSharedEditingContext.defaultSharedEditingContextClassName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.components.ERXToManyRelationship.checkBoxComponentName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.components.ERXToOneRelationship.radioButtonComponentName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.concurrency.ERXTaskObjectStoreCoordinatorPool.maxCoordinators", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.defaultModelGroupClassName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.eof.ERXQuery.removeForeignKeysFromRowValues", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.eof.ERXQuery.useBindVariables", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.eof.ERXQuery.useEntityRestrictingQualifiers", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXAdaptorationWrapper.postAdaptorOperationNotifications", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXAdaptorChannelDelegate.enabled", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXAdaptorChannelDelegate.trace.entityMatchPattern", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXAdaptorChannelDelegate.trace.maxLength", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXAdaptorChannelDelegate.trace.milliSeconds.debug", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXAdaptorChannelDelegate.trace.milliSeconds.error", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXAdaptorChannelDelegate.trace.milliSeconds.info", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXAdaptorChannelDelegate.trace.milliSeconds.warn", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXApplication.redirectOnMissingObjects", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXClassDescription.factoryClass", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXCustomObject.shouldTrimSpaces", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXCustomObject.useValidity", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXDatabase.className", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXDatabase.snapshotCacheMapInitialCapacity", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXDatabase.snapshotCacheMapInitialLoadFactor", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXDatabaseConsolePassword", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXDatabaseContext.activate", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXDatabaseContext.className", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXDatabaseContextDelegate.autoBatchFetchSize", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXDatabaseContextDelegate.Exceptions.regex", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXDatabaseContextDelegate.logTolerantEntityNotAvailable", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXDatabaseContextDelegate.tolerantEntityPattern", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEC.defaultAutomaticLockUnlock", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEC.defaultCoalesceAutoLocks", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEC.denyMerges", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEC.editingContextClassName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEC.markOpenLocks", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEC.safeLocking", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEC.traceOpenLocks", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEC.useSharedEditingContext", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEC.useUnlocker", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEnterpriseObject.applyRestrictingQualifierOnInsert", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEnterpriseObject.Observer.className", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEnterpriseObject.updateInverseRelationships", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEntityClassDescription.*.ClassName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEntityClassDescription.defaultClassName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEntityClassDescription.isFixingRelationshipsEnabled", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEntityClassDescription.isRapidTurnaroundEnabled", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEOAdaptorDebuggingPassword", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEOEncodingUtilities.EntityNameSeparator", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXEOEncodingUtilities.SpecifySeparatorInURL", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXGenericRecord.localizationShouldFallbackToDefaultLanguage", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXGenericRecord.shouldTrimSpaces", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXGenericRecord.useValidity", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXGlobalID.optimize", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXInQualifier.DefaultPadToSize", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXJDBCAdaptor.className", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXJDBCAdaptor.columnClassName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXJDBCAdaptor.ignoreJNDIConfiguration", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXJDBCAdaptor.switchReadWrite", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXJDBCAdaptor.useConnectionBroker", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXJDBCConnectionBroker.connectionPingEnabled", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXJDBCConnectionBroker.connectionPingInterval", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXJDBCConnectionBroker.connectionPingSQL", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXJDBCConnectionBroker.maxCheckout", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXJDBCConnectionBroker.maxConnections", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXJDBCConnectionBroker.minConnections", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXKeyValueQualifier.Contains.flatten", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXLongPrimaryKeyFactory.encodeEntityInPkValue", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXLongPrimaryKeyFactory.encodeHostInPkValue", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXLongPrimaryKeyFactory.hostCode", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXLongPrimaryKeyFactory.increaseBy", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModel.defaultEOEntityClassName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModel.useExtendedPrototypes", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.*.columnName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.*.externalName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.*.ignoreTypeMismatch", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.flattenPrototypes", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.ignoreTypeMismatch", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.modelClassName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.modelLoadOrder", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.patchedModelClassName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.patchModelsOnLoad", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.prototypeModelName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.prototypeModelNames", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.raiseOnUnmatchingConnectionDictionaries", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXModelGroup.sqlDumpDirectory", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXObjectStoreCoordinatorPool.maxCoordinators", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXObjectStoreCoordinatorPool.threadOSC", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXOpenEditingContextLockTracesPassword", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXPrimaryKeyBatchSize", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXRaiseOnMissingEditingContextDelegate", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXRemoteNotificationsCenter.group", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXRemoteNotificationsCenter.identifier", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXRemoteNotificationsCenter.localBindAddress", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXRemoteNotificationsCenter.maxPacketSize", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXRemoteNotificationsCenter.port", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXSequence.Increment", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXSequence.TableName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXSharedEOLoader.PatchSharedEOLoading", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXSQLExpressionTracker.collectLastStatements", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXSQLExpressionTracker.numberOfStatementsToCollect", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXValidationShouldPushChangesToObject", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.ERXWORepetition.eoSupport", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.jgroupsSynchronizer.groupName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.jgroupsSynchronizer.localBindAddress", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.jgroupsSynchronizer.multicastAddress", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.jgroupsSynchronizer.multicastPort", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.jgroupsSynchronizer.properties", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.KeyValueQualifierSQLGenerationSupport.handlesKeyPathWithDerivedAttribute", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.migration.ERX", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.migration.ERXMigration.useDatabaseSpecificMigrations", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.multicastSynchronizer.group", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.multicastSynchronizer.identifier", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.multicastSynchronizer.localBindAddress", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.multicastSynchronizer.maxPacketSize", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.multicastSynchronizer.port", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.multicastSynchronizer.whitelist", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.partials.enabled", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.remoteSynchronizer", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.remoteSynchronizer.enabled", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.remoteSynchronizer.excludeEntities", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.remoteSynchronizer.includeEntities", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.WOToManyRelationship.localizeDisplayKeysDefault", EOF_SUPPORT ),
		new ObsoleteProperty( "er.extensions.WOToOneRelationship.localizeDisplayKeysDefault", EOF_SUPPORT ),
		new ObsoleteProperty( "er.migration.*.lockClassName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.migration.createTablesIfNecessary", EOF_SUPPORT ),
		new ObsoleteProperty( "er.migration.JDBC.dbUpdaterTableName", EOF_SUPPORT ),
		new ObsoleteProperty( "er.migration.migrateAtStartup", EOF_SUPPORT ),
		new ObsoleteProperty( "er.migration.modelNames", EOF_SUPPORT ),
		new ObsoleteProperty( "er.migration.skipModelNames", EOF_SUPPORT ),
		new ObsoleteProperty( "OracleBatchMode", EOF_SUPPORT ) );

	/**
	 * Lists every property that is set and addresses a removed feature, with the reason, as a section of the startup
	 * banner. Obsolete properties are harmless - they're simply no longer read - so they don't stop the launch: an
	 * application carrying years of configuration can be tried on the framework as it is, and this list is its to-do
	 * list for cleaning up. Prints nothing when there are none.
	 */
	public static void printObsoleteProperties() {
		final List<String> lines = new ArrayList<>();

		for( final String propertyName : new TreeSet<>( System.getProperties().stringPropertyNames() ) ) {
			final String value = System.getProperty( propertyName );

			if( value == null || value.isEmpty() ) {
				continue;
			}

			for( final ObsoleteProperty obsolete : OBSOLETE_PROPERTIES ) {
				if( obsolete.matches( propertyName ) ) {
					lines.add( String.format( "%s%n    %s", propertyName, obsolete.message() ) );
					break;
				}
			}
		}

		if( !lines.isEmpty() ) {
			log.warn( "{} obsolete propert{} set, for features that no longer exist. See OBSOLETE PROPERTIES below.", lines.size(), lines.size() == 1 ? "y is" : "ies are" );
			System.out.println( "============= OBSOLETE PROPERTIES ==============" );
			lines.forEach( System.out::println );
			System.out.println();
		}
	}
}
