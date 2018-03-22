package models;

public class InsertedEntry extends Entry {
	private boolean isNew = false;

	public boolean getIsNew() {
		return isNew;
	}

	public void setIsNew(boolean isNew) {
		this.isNew = isNew;
	}
}
