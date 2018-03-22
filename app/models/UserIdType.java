package models;

public enum UserIdType {
	ID, KAID;

	@Override
	public String toString() {
		return name().toLowerCase();
	}
}
