const Criterion = (() => {
	const criteria = [];
	
	class Criterion extends Component {
		constructor(name = "", description = "", weight = "") {
			super(name, description, weight);
			criteria.push(this);
		}
		get value() {
			const [ name, weight ] = [...this.dom.querySelectorAll("input.mdl-textfield__input")].map(e => e.value);
			const description = this.dom.getElementsByTagName("textarea")[0].value;
			return { name, description, weight: parseInt(weight, 10) };
		}
		generateDom(nameText, descriptionText, weightText) {
			const tableRow = document.createElement("tr");
			
			const name = new MDLTextfield("Criterion name", true, null, null, nameText);
			const description = new MDLTextarea("Description", 3, true, descriptionText);
			const weight = new MDLTextfield("Percent weight", true, "100|[1-9]\\d|[1-9]", "The weight must be a number between 1 and 100", weightText);
			
			const icon = new DomComponent(document.createElement("i"));
			icon.dom.className = "material-icons";
			icon.dom.innerHTML = "close";
			icon.dom.addEventListener("click", () => { 
				if(criteria.length > 1) {
					this.remove();
					criteria.splice(criteria.indexOf(this), 1);
				}
			});
			
			[name, description, weight, icon].forEach(e => {
				const td = document.createElement("td");
				e.appendTo(td);
				tableRow.appendChild(td);
			});
			
			return tableRow;
		}
		static get criteria() {
			return criteria;
		}
		static get values() {
			return criteria.map(e => e.value);
		}
		static get weightSum() {
			return this.values.map(e => e.weight).reduce((a, b) => a + b);
		}
	}
	
	return Criterion;
})();

const Bracket = (() => { 
	const brackets = [];
	
	class Bracket extends Component {
		constructor(onDelete = () => {}) {
			super(onDelete);
			brackets.push(this);
		}
		get value() {
			return this.textfield.value;
		}
		set value(v) {
			this.textfield.value = v;
		}
		generateDom(onDelete) {
			const item = document.createElement("div");
			item.className = "mdl-list__item-primary-content";
			
			const textfield = new MDLTextfield("Bracket name", true);
			this.textfield = textfield;
			
			const icon = document.createElement("i");
			icon.className = "material-icons mdl-list__item-icon";
			icon.innerHTML = "close";
			icon.addEventListener("click", e => {
				 this.remove();
				 brackets.splice(brackets.indexOf(this), 1);
				 onDelete();
			});
			
			textfield.appendTo(item);
			item.appendChild(icon);
			
			return item;
		}
		static get brackets() {
			return brackets;
		}
		static get bracketNames() {
			return brackets.map(e => e.value);
		}
	}
	
	return Bracket;
})();