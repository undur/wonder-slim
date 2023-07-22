var AUC = {
	register: function(id, options) {
		if (!options) {
			options = {};
		}
		eval(id + "Update = function() {AjaxUpdateContainer.update(id, options) }");
	}
}

var AUL = {
	update: function(id, options, elementID, queryParams) {
		
		// Just some logging. For fun.
		console.log( "===== Clicked AjaxUpdateLink =====")
		console.log( "id: " + id );
		console.log( "options: " + options );
		console.log( "elementID: " + elementID );
		console.log( "queryParams: " + queryParams );
		
		// This is the updateContainer we're going to target
		var updateContainer = document.getElementById(id);
		
		
//		if( updateContainer ) {
//			alert('No AjaxUpdateContainer on the page with id ' + id);
//		}

		var actionUrl = updateContainer.getAttribute('data-updateUrl');

		// We cleverly replace the elementID on the UC to the clicked link's element ID
		actionUrl = actionUrl.replace(/[^\/]+$/, elementID);
		actionUrl = actionUrl + '?_u=' + id;
		invokeUpdate( id, actionUrl );
	}
}

/*
 * Very, very extermely experimental and rudimentary observefield support 
 */
var ASB = {
	observeField: function(updateContainerID, formFieldID, observeFieldFrequency, partial, observeDelay, options) {
		var fieldElement = document.getElementById( formFieldID );

		console.log( fieldElement );
		console.log( 'ble' );

		// Invoked if the field is exited (blurred) by user action
		fieldElement.addEventListener( "blur" , function() {
			console.log('blurblur');
		} );		
		
		// FIXME: We're not handling frequency/delay etc.
	}
}

function invokeUpdate( id, url ) {
	const xhttp = new XMLHttpRequest();
	xhttp.open("GET", url, true);
	
	xhttp.onload = () => {
		var updateContainer = document.getElementById(id);
		updateContainer.innerHTML = xhttp.responseText;
    };
	
	xhttp.send();
}