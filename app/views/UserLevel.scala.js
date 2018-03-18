@import models.UserLevel

@()

(function() {
	function Item(index, name) {
		this.index = index;
		this.n = name;
		Object.freeze(this);
	}
	
	Item.prototype = {
		ordinal: function() {
			return this.index;
		},
		name: function() {
			return this.n;
		},
		toString: function() {
			return this.name();
		}
	};
	
	function Enum(items) {
		var item, i;
		this.values = [];
		for(i = 0; i < items.length; i++) {
			item = items[i];
			this[item] = new Item(i, item);
			this.values.push(this[item]);
		}
		Object.freeze(this.values);
		Object.freeze(this);
	}
	
	Object.defineProperty(window, "UserLevel", {
		value: new Enum([
			@for(i <- 0 to (models.UserLevel.values().length - 1)) {
				"@models.UserLevel.values()(i).name"
				@if(i != (models.UserLevel.values().length - 1)) {
					,
				}
			}
		]),
		writable: false, 
		enumerable: true, 
		configurable: true
	});
})();