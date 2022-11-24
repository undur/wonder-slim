// FIXME: Just including this as a shorthand for now
var $ = function( id ) { return document.getElementById( id ); };

function addQueryParameters( someString, additionalParameters) {
	if (additionalParameters) {
		return someString + (someString.match(/\?/) ? '&' : '?') + additionalParameters;
	}
	else {
		return someString;
	}
}

function invokeUpdate( id, url ) {	
	const xhttp = new XMLHttpRequest();
	xhttp.open("GET", url, false);
	xhttp.send();
	console.log( "Requested URL: " + url );
	console.log( "Received content: " + xhttp.responseText )
	document.getElementById(id).innerHTML = xhttp.responseText;
}

var AjaxUpdateContainer = {
	// FIXME: Implement
	/*  
	registerPeriodic: function(id, canStop, stopped, options) {
		var url = $(id).getAttribute('data-updateUrl');
		var updater;
		if (!canStop) {
			updater = new Ajax.PeriodicalUpdater(id, url, options);
		}
		else if (stopped) {
			var newOptions = Object.extend({}, options);
			newOptions.stopped = true;
			updater = new Ajax.StoppedPeriodicalUpdater(id, url, newOptions);
		}
		else {
			updater = new Ajax.ActivePeriodicalUpdater(id, url, options);
		}
		
		eval(id + "PeriodicalUpdater = updater;");
		eval(id + "Stop = function() { " + id + "PeriodicalUpdater.stop() };");
	},
	*/

	// FIXME: Implement
	/* 
	insertionFunc: function(effectPairName, beforeDuration, afterDuration) {
		var insertionFunction;
		
		var showEffect = 0;
		var hideEffect = 1;
		
		for (var existingPairName in Effect.PAIRS) {
			var pairs = Effect.PAIRS[existingPairName];
	
			if (effectPairName == existingPairName) {
				insertionFunction = function(receiver, response) {
					Effect[Effect.PAIRS[effectPairName][hideEffect]](receiver, { 
						duration: beforeDuration || 0.5,
						afterFinish: function() { 
							receiver.update(response); 
							Effect[Effect.PAIRS[effectPairName][showEffect]](receiver, {
									duration: afterDuration || 0.5
							});
						}
					});
				};
			}
			else if (effectPairName == pairs[hideEffect]) {
				insertionFunction = function(receiver, response) {
					Effect[effectPairName](receiver, { 
						duration: beforeDuration || 0.5,
						afterFinish: function() { 
							receiver.update(response);
							receiver.show();
						}
					});
				};
			}
			else if (effectPairName == pairs[showEffect]) {
				insertionFunction = function(receiver, response) {
					receiver.hide();
					receiver.update(response); 
					Effect[effectPairName](receiver, {
						duration: afterDuration || 0.5
					});
				};
			}
		}
		
		return insertionFunction;
	},
	*/
	
	register: function(id, options) {
		if (!options) {
			options = {};
		}
		eval(id + "Update = function() {AjaxUpdateContainer.update(id, options) }");
	},
	
	update: function(id, options) {
		var updateElement = $(id);
		if (updateElement == null) {
			alert('There is no element on this page with the id "' + id + '".');
		}
		var actionUrl = updateElement.getAttribute('data-updateUrl');
		if (options && options['_r']) {
			actionUrl = addQueryParameters(actionUrl,'_r='+ id);
		}
		else {
			actionUrl = addQueryParameters(actionUrl,'_u='+ id);
		}
		actionUrl = addQueryParameters(actionUrl,new Date().getTime());
		new Ajax.Updater(id, actionUrl, AjaxOptions.defaultOptions(options));
	}
};
var AUC = AjaxUpdateContainer;

var AjaxUpdateLink = {
	updateFunc: function(id, options, elementID) {
		var updateFunction = function(queryParams) {
			AjaxUpdateLink.update(id, options, elementID, queryParams);
		};
		return updateFunction;
	},
	
	update: function(id, options, elementID, queryParams) {
		var updateElement = $(id);
		if (updateElement == null) {
			alert('There is no element on this page with the id "' + id + '".');
		}
		AjaxUpdateLink._update(id, updateElement.getAttribute('data-updateUrl'), options, elementID, queryParams);
	},
	
	_update: function(id, actionUrl, options, elementID, queryParams) {
		if (elementID) {
			actionUrl = actionUrl.sub(/[^\/]+$/, elementID);
		}
		actionUrl = addQueryParameters(actionUrl,queryParams);
		if (options && options['_r']) {
			actionUrl = addQueryParameters(actionUrl,'_r='+ id);
		}
		else {
			actionUrl = addQueryParameters(actionUrl,'_u='+ id);
		}
		actionUrl = addQueryParameters(actionUrl,new Date().getTime());
		new Ajax.Updater(id, actionUrl, AjaxOptions.defaultOptions(options));
	},
	
	request: function(actionUrl, options, elementID, queryParams) {
		if (elementID) {
			actionUrl = actionUrl.sub(/[^\/]+$/, elementID);
		}
		actionUrl = addQueryParameters(actionUrl,queryParams);
		new Ajax.Request(actionUrl, AjaxOptions.defaultOptions(options));
	}
};

var AUL = AjaxUpdateLink;
