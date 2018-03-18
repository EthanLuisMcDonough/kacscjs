const getCookies = () => {
    let cookies = {};
    let arrCookies = document.cookie.split(";").forEach(e => {
        let ps = e.split("=");
        let key = decodeURIComponent(ps[0].trim()), 
            value = decodeURIComponent(ps[1].trim());
        cookies[key] = value;
    });
    return cookies;
};

(function() {
	const ctoken = getCookies()["ftok"];
	Object.defineProperty(window, "CSRF_HEADER", {
		value: "x-ftok",
		writable: false, 
		enumerable: true, 
		configurable: true
	});
	if(ctoken) {
		Object.defineProperty(window, "CSRF_TOKEN", {
			value: ctoken,
			writable: false, 
			enumerable: true, 
			configurable: true
		});
	}
})();