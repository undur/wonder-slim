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
		console.log( "id: " + id );
		console.log( "options: " + options );
		console.log( "elementID: " + elementID );
		console.log( "queryParams: " + queryParams );
		
		invokeUpdate( id, '/Apps/WebObjects/Hugi.woa/ajax/' + elementID + '?_u=someUC');
	}
}

function invokeUpdate( id, url ) {
	console.log( "Requested URL: " + url );
	const xhttp = new XMLHttpRequest();
	xhttp.open("GET", url, false);
	xhttp.send();
	console.log( "Received content: " + xhttp.responseText )

	var updateContainer = document.getElementById(id);
	updateContainer.innerHTML = xhttp.responseText;
}
