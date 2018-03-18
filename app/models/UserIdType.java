package models;

public enum UserIdType {
	ID, KAID;

	public String toString() {
		return name().toLowerCase();
	}
}
