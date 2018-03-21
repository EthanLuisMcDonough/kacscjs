const Component = (() => {
	const ALPHANUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
	const randomSequence = length => 
		[...Array(length)].map(e => ALPHANUM[Math.floor(Math.random() * ALPHANUM.length)])
		.join("");
	
	const ITEMS = {};
	const ID_LENGTH = 75;
	
	const genId = () => `component-${randomSequence(ID_LENGTH)}`;
	
	return class Component {
		constructor() {
			let key = genId();
			while(ITEMS[key])
				key = genId();
			ITEMS[key] = this;
			this.dom = this.generateDom(...arguments);
			this.dom.id = key;
		}
		get ID() {
			return this.dom.id;
		}
		generateDom() {
			return document.createElement("span");
		}
		appendTo(el) {
			(el instanceof Component ? el.dom : el).appendChild(this.dom);
		}
		remove() {
			if (this.dom.parentElement) { 
				this.dom.parentElement.removeChild(this.dom); 
			}
		}
		static registerComponent(domEl) {
			return new DomComponent(domEl);
		}
	}
})(); 

const DomComponent = class DomComponent extends Component {
	constructor(dom) {
		super(dom);
	}
	generateDom(dom) {
		return dom;
	}
}

const MDLSlider = class MDLSlider extends Component {
	constructor(min = 0, max = 0, value = 0) {
		super(min, max, value);
	}
	get value() {
		return this.dom.value;
	}
	set value(n) {
		this.dom.value = n;
	}
	addOnInput(fn) {
		if(typeof fn == "function") {
			this.dom.addEventListener("input", fn);
		}
	}
	generateDom(min, max, value) {
		const input = document.createElement("input");
		input.className = "mdl-slider mdl-js-slider";
		input.setAttribute("type", "range");
		input.setAttribute("min", min);
		input.setAttribute("max", max);
		input.setAttribute("value", value);
		input.setAttribute("tabindex", 0);
		return input;
	}
	update() {
		componentHandler.upgradeElement(this.dom);
	}
}

const MDLTextfield = class MDLTextfield extends Component {
	constructor(label, required = false, pattern = null, errorText = null) {
		super(label, required, pattern, errorText);
	}
	getTextContent() {
		return this.dom.getElementsByClassName("mdl-textfield__input")[0].value;
	}
	generateDom(lbl, required, pattern, errorText) {
		const textfield = document.createElement("span");
		textfield.className = "mdl-textfield mdl-js-textfield";
		
		const textInput = new DomComponent(document.createElement("input"));
		textInput.dom.setAttribute("type", "text");
		textInput.dom.className = "mdl-textfield__input";
		
		if (pattern) { textInput.dom.setAttribute("pattern", pattern); }
		if (required) { textInput.dom.setAttribute("required", true); }
		
		const label = document.createElement("label");
		label.textContent = lbl;
		label.className = "mdl-textfield__label";
		label.setAttribute("for", textInput.ID);
		
		textInput.appendTo(textfield);
		textfield.appendChild(label);
		
		if (errorText) {
			const errorSpan = document.createElement("span");
			errorSpan.className = "mdl-textfield__error";
			errorSpan.textContent = errorText;
			textfield.appendChild(errorSpan);
		}
		
		componentHandler.upgradeElement(textfield);
		
		return textfield;
	}
}

const MDLTextarea = class MDLTextarea extends Component {
	constructor(label, rows = 3, required = false) {
		super(label, rows, required);
	}
	getValue() {
		return this.dom.getElementsByClassName("mdl-textfield__input")[0].value;
	}
	generateDom(lbl, rows, required) {
		const textfield = document.createElement("span");
		textfield.className = "mdl-textfield mdl-js-textfield";
		
		const textInput = new DomComponent(document.createElement("textarea"));
		textInput.dom.setAttribute("type", "text");
		textInput.dom.className = "mdl-textfield__input";
		textInput.dom.setAttribute("rows", rows);
		
		if (required) { textInput.dom.setAttribute("required", true); }
		
		const label = document.createElement("label");
		label.textContent = lbl;
		label.className = "mdl-textfield__label";
		label.setAttribute("for", textInput.ID);
		
		textInput.appendTo(textfield);
		textfield.appendChild(label);
		
		componentHandler.upgradeElement(textfield);
		
		return textfield;
	}
}

const MDLCheckbox = class MDLCheckbox extends Component {
	constructor() {
		super();
	}
	get checked() {
		return this.check.dom.checked;
	}
	set checked(n) {
		if (typeof n == "boolean") {
			this.check.dom.checked = n;
		}
	}
	generateDom() {
		const label = document.createElement("label");
		label.className = "mdl-checkbox mdl-js-checkbox mdl-js-ripple-effect mdl-data-table__select";
		
		const checkbox = new DomComponent(document.createElement("input"));
		checkbox.dom.setAttribute("type", "checkbox");
		checkbox.dom.className = "mdl-checkbox__input";
		
		checkbox.appendTo(label);
		
		label.setAttribute("for", checkbox.ID);
		
		componentHandler.upgradeElement(label);
		
		this.check = checkbox;
		return label;
	}
}

const TableRow = class TableRow extends Component {
	constructor() {
		super([...arguments]);
		this.values = [...arguments];
	} 
	at(index) {
		return this.values[index];
	}
	generateDom(values) {
		const row = document.createElement("tr");
		values.forEach(value => {
			const item = document.createElement("td");
			if (value instanceof Element) 
				item.appendChild(value);
			else if (value instanceof Component)
				value.appendTo(item);
			else
				item.textContent = value;
			row.appendChild(item);
		})
		return row;
	}
}

const MDLTable = class MDLTable extends Component {
	constructor(columns = [], rows = []) {
		super(columns, rows);
		this.columns = columns;
		this.rows = rows;
	}
	addRow(row) {
		this.rows.push(row);
		row.appendTo(this.dom.getElementsByTagName("tbody")[0]);
	}
	removeRow(index) {
		this.rows[index].remove();
		this.rows.splice(index, 1);
	}
	generateDom(columns, rows) {
		const table = document.createElement("table");
		table.className = "mdl-data-table mdl-data-table--selectable mdl-shadow--2dp";
		
		const thead = document.createElement("thead");
		
		const headRow = document.createElement("tr")
		columns.forEach(column => {
			const tableHeader = document.createElement("th");
			if (column instanceof Element)
				tableHeader.appendChild(column);
			else if (column instanceof Component)
				column.appendTo(tableHeader);
			else 
				tableHeader.textContent = column;
			headRow.appendChild(tableHeader);
		});
		
		thead.appendChild(headRow);
		
		const tbody = document.createElement("tbody");
		
		rows.forEach(row => {
			const bodyRow = document.createElement("tr");
			row.values.forEach(value => {
				const item = document.createElement("td");
				if (value instanceof Element)
					item.appendChild(value);
				else if (value instanceof Component)
					value.appendTo(item);
				else 
					item.textContent = value;
				bodyRow.appendChild(item);
			});
			tbody.appendChild(bodyRow);
		});
		
		table.appendChild(thead);
		table.appendChild(tbody);
		
		componentHandler.upgradeElement(table);
		
		return table;
	}
}

const MDLCheckTable = (() => {
	const CHECK_BANK = {};
	
	const PRIVATE = {
		setChecks(array = []) {
			CHECK_BANK[this.ID] = array;
		},
		getChecks() {
			return CHECK_BANK[this.ID];
		}
	};
	
	return class MDLCheckTable extends MDLTable {
		constructor(columns = [], rows = []) {
			const checks = [];
			const masterCheck = new MDLCheckbox();
			
			columns.unshift(masterCheck);
			checks.push(masterCheck);
			
			rows.forEach(e => {
				const check = new MDLCheckbox();
				e.values.unshift(check);
				checks.push(check);
			});
			
			super(columns, rows);
			
			PRIVATE.setChecks.call(this, checks);
			
			masterCheck.check.dom.addEventListener("change", e => PRIVATE.getChecks.call(this)
					.forEach(check => check.checked != masterCheck.checked && check.dom.click()));
		}
		get checkedRows() {
			return this.rows.filter(e => e.at(0) && e.at(0).checked);
		}
		isChecked(row) {
			return this.rows[row] && this.rows[row].at(0) && 
				this.rows[row].at(0).checked;
		}
		addRow() {
			const values = [...arguments];
			const check = new MDLCheckbox();
			values.unshift(check);
			const newRow = new TableRow(...values);
			
			PRIVATE.getChecks.call(this).push(check);
			
			this.rows.push(newRow);
			newRow.appendTo(this.dom.getElementsByTagName("tbody")[0]);
		}
	}
})();

const MDLDialog = class MDLDialog extends Component {
	constructor(title, text, buttons) {
		super(title, text, buttons);
	}
	showModal() {
		this.dom.showModal();
	}
	close() {
		this.dom.close();
	}
	generateDom(title, text, buttons) {
		const dialog = document.createElement("dialog");
		dialog.className = "mdl-dialog delete-dia mdl-shadow--16dp";
		if (!dialog.showModal) {
			dialogPolyfill.registerDialog(dialog);
		}
		
		const dialogTitle = document.createElement("h3");
		dialogTitle.textContent = title;
		dialogTitle.className = "mdl-dialog__title";
		dialog.appendChild(dialogTitle);
		
		const dialogContent = document.createElement("div");
		dialogContent.className = "mdl-dialog__content";
		
		if(text instanceof Element) {
			dialogContent.appendChild(text);
		} else {
			const dialogText = document.createElement("p");
			dialogText.textContent = text;
			dialogContent.appendChild(dialogText);
		}
		dialog.appendChild(dialogContent);
		
		const dialogActions = document.createElement("div");
		dialogActions.className = "mdl-dialog__actions";
		
		buttons.forEach(button => {
			const btn = document.createElement("button");
			btn.setAttribute("type", "button");
			btn.className = "mdl-button";
			btn.style.color = typeof button.color == "string" ? button.color : "#000000";
			btn.textContent = typeof button.label == "string" ? button.label : "Text";
			if (typeof button.onClick == "function") {
				btn.addEventListener("click", button.onClick);
			}
			dialogActions.appendChild(btn);
		});
		
		dialog.appendChild(dialogActions);
		
		return dialog;
	}
}

const MDLAccentRippleBtn = class MDLAccentRippleBtn extends Component {
	constructor(label) {
		super(label);
	}
	addOnClick(func) {
		if(typeof func == "function") {
			this.dom.addEventListener("click", func);
		}
	}
	enable() {
		this.dom.disabled = false;
	}
	disable() {
		this.dom.disabled = true;
	}
	generateDom(label) {
		const button = document.createElement("button");
		button.textContent = label;
		button.setAttribute("type", "button");
		button.className = "mdl-button mdl-js-button mdl-button--raised mdl-js-ripple-effect mdl-button--accent";
		componentHandler.upgradeElement(button);
		return button;
	}
}

const MDLMenu = class MDLMenu extends Component {
	constructor(items = [], icon = "more_vert", side = "left", vert = "bottom", menuDest = null) {
		super(items, icon, side, vert);
		this.menuDest = menuDest;
	}
	generateDom(items, icon, side, vert) {
		const ico = document.createElement("i");
		ico.className = "material-icons";
		ico.textContent = icon;
		
		const button = new DomComponent(document.createElement("button"));
		button.dom.className = "mdl-button mdl-js-button mdl-js-ripple-effect mdl-button--icon";
		button.dom.appendChild(ico);
		
		const contextMenu = document.createElement("ul");
		contextMenu.className = `mdl-menu mdl-js-menu mdl-menu--${vert}-${side}`;
		contextMenu.setAttribute("for", button.dom.id);
		
		items.forEach(item => {
			const el = document.createElement("li");
			el.textContent = item.text;
			el.className = "mdl-menu__item";
			if(item.disabled) 
				el.setAttribute("disabled", true);
			else
				el.addEventListener("click", item.onClick);
			contextMenu.appendChild(el);
		});
		
		this.contextMenu = contextMenu;
		
		const s = document.createElement("span");
		
		button.appendTo(s);
		componentHandler.upgradeElement(button.dom);
		
		return s;
	}
	appendTo(el) {
		el.style.position = "relative";
		super.appendTo(el);
		(this.menuDest || el).appendChild(this.contextMenu);
		componentHandler.upgradeElement(this.contextMenu);
	}
}

const PageIterator = class PageIterator {
	constructor(getRoute, limit = 10) {
		this.page = 0;
		this.limit = limit;
		if (typeof getRoute == "function") 
			this.getRoute = getRoute;
		else 
			throw new TypeError("getSet must be a function that returns a route");
	}
	next() {
		return new Promise((res, rej) => {
			const route = this.getRoute(this.page++, this.limit);
			fetch(route.url, {
				method: route.method,
				headers: {
					"X-Requested-With": "fetch",
					[CSRF_HEADER]: CSRF_TOKEN
				},
				credentials: "same-origin"
			}).then(response => response.status >= 200 && response.status < 300 ? 
					response.json() : Promise.reject(response)).then(data => {
						res({
							value: data,
							done: data.length < this.limit
						});
			}).catch(rej);
		})
	}
}

const IteratorTable = class IteratorTable extends Component {
	constructor(table, iterator, itemCallback, otherButtons = []) {
		super(table);
		this.table = table;
		this.iterator = iterator;
		this.itemCallback = itemCallback;
		this.loadMore = new MDLAccentRippleBtn("Load more");
		this.otherButtons = otherButtons;
		
		this.loadMore.addOnClick(e => {
			this.loadMore.disable();
			this.next().then(data => this.loadMore[data.done ? "remove" : "enable"]()).catch(console.error);
		});
	}
	init() {
		this.next().then(data => {
			if(!data.done)
				this.loadMore.appendTo(this.buttons);
			this.otherButtons.forEach(e => {
				if (e instanceof Component)
					e.appendTo(this.buttons);
				else if (e instanceof Element)
					this.buttons.appendChild(e);
			});
		})
	}
	insertItems(data) {
		data.value.forEach(this.itemCallback.bind(this));
		return data;
	}
	next() {
		return this.iterator.next().then(this.insertItems.bind(this));
	}
	generateDom(table) {
		const area = document.createElement("div");
		table.appendTo(area);
		const buttons = document.createElement("div");
		buttons.className = "button-div";
		this.buttons = buttons;
		area.appendChild(buttons);
		return area;
	}
} 